package org.ruffalo.syndo.executions;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.ImageStreamTag;
import io.fabric8.openshift.api.model.ProjectBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.ruffalo.syndo.build.BuildResult;
import org.ruffalo.syndo.build.BuilderAction;
import org.ruffalo.syndo.build.ComponentBuildAction;
import org.ruffalo.syndo.build.SyndoBuiderAction;
import org.ruffalo.syndo.cmd.Command;
import org.ruffalo.syndo.cmd.CommandBuild;
import org.ruffalo.syndo.config.Component;
import org.ruffalo.syndo.config.Loader;
import org.ruffalo.syndo.config.Root;
import org.ruffalo.syndo.model.BuildNode;
import org.ruffalo.syndo.model.FileSourceNode;
import org.ruffalo.syndo.model.ImageRefSourceNode;
import org.ruffalo.syndo.resources.SyndoTarCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * An execution object is created and configured for an execution. It needs the details necessary to build
 * an OpenShift client and the location of the build yaml file.
 */
public class BuildExecution extends Execution {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private Command command;

    public BuildExecution(final Command command) {
        this.command = command;
    }

    public ExecutionResult execute() {
        final CommandBuild buildCommand = this.command.getBuild();
        final Path pathToConfig = buildCommand.getBuildFile();
        if (!Files.exists(pathToConfig)) {
            logger.error("No build yaml could be found at path: {}", pathToConfig);
            return new ExecutionResult(1);
        }

        final ExecutionResult result = new ExecutionResult();

        // create openshift client
        final List<Path> openshiftConfigSearchPaths = this.command.getOpenshiftConfigSearchPaths();
        Config openshiftClientConfig = null;
        if (!openshiftConfigSearchPaths.isEmpty()) {
            for (final Path path : openshiftConfigSearchPaths) {
                // start with root config path
                Path configPath = path.resolve("config").normalize().toAbsolutePath();

                // if the file does not exist search for kubeconfig
                if (!Files.exists(configPath)) {
                    configPath = path.resolve("kubeconfig").normalize().toAbsolutePath();
                    // if kubeconfig does not exist move on
                    if (!Files.exists(configPath)) {
                        continue;
                    }
                }

                // otherwise create config client
                try {
                    openshiftClientConfig = Config.fromKubeconfig(null, new String(Files.readAllBytes(configPath)), configPath.toString());
                    logger.info("Using kube configuration: {}", configPath);
                    break;
                } catch (IOException e) {
                    // skip
                }
            }
        }
        // if no configuration found use default
        if (openshiftClientConfig == null) {
            openshiftClientConfig = new ConfigBuilder().build();
            logger.info("Using default kube configuration");
        }

        // load syndo root configuration
        final Root config = Loader.read(pathToConfig);
        // todo: verify config and report
        logger.info("Using build file: {}", pathToConfig);

        // create map of components so we can handle ordering/dependencies
        final Map<String, Component> componentMap = new HashMap<>();
        config.getComponents().forEach(component -> componentMap.put(component.getName(), component));

        // todo: filter components that are being built according to the requested components
        //       and aliases


        // create output build tar
        Path outputTar = buildCommand.getTarOutput();
        if (outputTar != null) {
            outputTar = outputTar.normalize().toAbsolutePath();
            logger.info("Build tar: {}", outputTar);
        } else {
            final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
            outputTar = fs.getPath("build-output.tar.gz").normalize().toAbsolutePath();
        }

        // now we can start a build...
        try (
            final KubernetesClient k8s = new DefaultOpenShiftClient(openshiftClientConfig);
            final OpenShiftClient client = k8s.adapt(OpenShiftClient.class)
       ) {
            // check kubernetes client
            final URL ocUrl = client.getOpenshiftUrl();
            if (ocUrl == null) {
                logger.error("No OpenShift url available");
                result.setExitCode(1);
                return result;
            }
            logger.info("OpenShift: {}", ocUrl);

            // get namespace
            String tmpNamespace = buildCommand.getNamespace();
            if (tmpNamespace == null || tmpNamespace.isEmpty()) {
                tmpNamespace = client.getNamespace();
            }
            final String namespace = tmpNamespace;

            // todo: warn about 'dangerous' namespaces (default, etc)

            // create or use existing namespace
            if (!buildCommand.isDryRun() && client.projects().withName(namespace).get() == null) {
                client.projects().createOrReplace(new ProjectBuilder().withMetadata(new ObjectMetaBuilder().withName(namespace).build()).build());
                logger.debug("Created namespace: {}", namespace);
            }
            logger.info("Namespace: {}", namespace);

            // build everything into the node map
            final Map<String, String> outputRefResolveMap = new HashMap<>();
            final Map<String, FileSourceNode> sourceNodeMap = new HashMap<>();
            config.getComponents().forEach(component -> {
                final FileSourceNode node = new FileSourceNode(component.getName());

                // todo: maybe do a better job of resolving the target path
                Path componentDir = Paths.get(component.getPath());
                if (!componentDir.isAbsolute()) {
                    if (!Files.exists(componentDir)) {
                        // try and resolve relative to build yaml
                        componentDir = pathToConfig.getParent().resolve(componentDir).normalize().toAbsolutePath();
                    }
                }
                if (!Files.exists(componentDir)) {
                    logger.error("Could not resolve directory '{}' for component '{}', skipping", component.getPath(), component.getName());
                    return;
                }
                node.setDirectory(componentDir);

                // todo: do a better job of resolving the output reference
                String to = component.getTo();
                if (to == null || to.isEmpty()) {
                    to = component.getName();
                }
                node.setOutputRef(this.resolveOutputRef(buildCommand, client, namespace, to));
                outputRefResolveMap.put(node.getOutputRef(), node.getName());

                // add to map
                sourceNodeMap.put(component.getName(), node);
            });

            // go through again and resolve "from" references
            sourceNodeMap.forEach((key, node) -> {
                final Component component = componentMap.get(key);
                if (component == null) {
                    return;
                }
                final FileSourceNode from = sourceNodeMap.get(component.getFrom());

                if (from == null) {
                    // set the dockerfile path on the component using the files in the resolved node directory
                    if (component.getDockerfile() != null && !component.getDockerfile().isEmpty()) {
                        final Path dockerfilePath = node.getDirectory().resolve(component.getDockerfile()).normalize().toAbsolutePath();
                        if (!Files.exists(dockerfilePath)) {
                            logger.error("No dockerfile resolved for '{}' at path '{}'", component.getName(), dockerfilePath);
                        }
                        node.setDockerfile(component.getDockerfile());
                        List<String> dockerLines = null;
                        try {
                            dockerLines = Files.readAllLines(dockerfilePath);
                        } catch (IOException e) {
                            logger.error("Could not read dockerfile {}", dockerfilePath);
                        }
                        if (dockerLines == null) {
                            dockerLines = Collections.emptyList();
                        }
                        if (dockerLines.isEmpty()) {
                            logger.error("Empty dockerfile {} provided", dockerfilePath);
                        }
                        String fromRef = "";
                        for(final String line : dockerLines) {
                            if (line.trim().toUpperCase().startsWith("FROM")) {
                                fromRef = line.substring(4).trim();
                                break;
                            }
                        }
                        if (fromRef.isEmpty()) {
                            logger.error("Could not read FROM image in {}", dockerfilePath);
                        } else {
                            FileSourceNode fromNode = sourceNodeMap.get(fromRef);
                            if (fromNode == null) {
                                final String nodeName = outputRefResolveMap.get(fromRef);
                                fromNode = sourceNodeMap.get(nodeName);
                            }
                            // todo: figure out how to feed the resolved reference back into the docker build file
                            if (fromNode != null) {
                                node.setFrom(fromNode);
                            } else {
                                this.setFromImageRef(buildCommand, node, client, namespace, fromRef);
                            }
                        }
                    } else {
                        this.setFromImageRef(buildCommand, node, client, namespace, component.getFrom());
                    }
                } else {
                    node.setFrom(from);
                    // keep the from node
                    from.setKeep(true);
                }
            });

            // todo: check for cyclic dependencies

            // ensure that the build list is the nodes, in order
            final List<FileSourceNode> buildOrder = new LinkedList<>();
            while(!sourceNodeMap.isEmpty()) {
                final Set<Map.Entry<String, FileSourceNode>> entrySet = new HashSet<>(sourceNodeMap.entrySet());
                for (Map.Entry<String, FileSourceNode> entry : entrySet) {
                    final FileSourceNode node = entry.getValue();
                    final BuildNode from = node.getFrom();
                    // if it starts from an image reference it can go instantly
                    if (from instanceof ImageRefSourceNode) {
                        buildOrder.add(node);
                        sourceNodeMap.remove(node.getName());
                        continue;
                    }

                    // if the parent file source node has been removed from the map
                    // then this node is cleared to go
                    if (from instanceof FileSourceNode) {
                        final FileSourceNode fromFileSource = (FileSourceNode) from;
                        if (sourceNodeMap.get(fromFileSource.getName()) == null) {
                            buildOrder.add(node);
                            sourceNodeMap.remove(node.getName());
                            continue;
                        }
                    }

                    // do nothing
                }
            }

            // go through the build order and ensure that there are image streams in the namespace for eac
            // output reference


            try (final TarArchiveOutputStream tarStream = SyndoTarCreator.createTar(outputTar)) {
                // walk through the build components, add them to the tar, and then add metadata to them
                // we do this with an indexed for loop so we can get the build order built out in a way
                // that bash will understand
                for (int i = 0; i < buildOrder.size(); i++) {
                    final FileSourceNode sourceNode = buildOrder.get(i);
                    final String prefix = String.format("%04d_%s", i, sourceNode.getName());
                    logger.info("Adding {} with context {} to {}", sourceNode.getName(), sourceNode.getDirectory(), prefix);

                    // now add metadata to prefix
                    final Map<String, String> meta = new HashMap<>();
                    meta.put("COMPONENT", sourceNode.getName());
                    meta.put("OUTPUT_TARGET", sourceNode.getOutputRef());

                    final String fromRef = sourceNode.getFromRef();
                    final String dockerfile = sourceNode.getDockerfile();
                    if (dockerfile != null && !dockerfile.isEmpty()) {
                        meta.put("DOCKERFILE", sourceNode.getDockerfile());
                    } else if (fromRef != null) {
                        meta.put("FROM_IMAGE", fromRef);
                    }

                    if (sourceNode.isKeep()) {
                        meta.put("KEEP", "true");
                    }

                    // add files to tar
                    SyndoTarCreator.addPrefixedDirectoryToTar(tarStream, sourceNode.getDirectory(), prefix);
                    SyndoTarCreator.addMetaEnvToTar(tarStream, prefix + "/.meta/env", meta);
                }
            } catch (IOException e) {
                logger.error("Could not create tar output stream for build", e);
                result.setExitCode(1);
                return result;
            }

            // if dry run we can stop now
            if (buildCommand.isDryRun()) {
                logger.info("Ending dry run");
                return result;
            }

            // todo: better resolution of bootstrap directory relative to current path
            Path bootstrapDirectory = buildCommand.getBootstrapRoot();
            if (bootstrapDirectory != null && !Files.exists(bootstrapDirectory)) {
                bootstrapDirectory = null;
            }
            final SyndoBuiderAction action1 = new SyndoBuiderAction(namespace, bootstrapDirectory);
            if (bootstrapDirectory != null || buildCommand.isForceBootstrap()) {
                action1.setForceBuild(true);
            }

            final BuildResult r1 = action1.build(client);
            if (r1.getStatus().equals(BuildResult.Status.FAILED)) {
                result.setExitCode(1);
                return result;
            }

            final ComponentBuildAction action2 = new ComponentBuildAction(namespace, outputTar);
            final BuildResult r2 = action2.build(client);
            if (r2.getStatus().equals(BuildResult.Status.FAILED)) {
                result.setExitCode(1);
                return result;
            }
        } catch (KubernetesClientException kce) {
            logger.error("Error connecting to kubernetes: {}", kce.getMessage());
        }

        return result;
    }

    private String resolveOutputRef(final CommandBuild build, final OpenShiftClient client, final String namespace, final String imageRef) {
        // if the name is just 'imageRef' return the image ref on the namespace
        if (!imageRef.contains("/") && !imageRef.contains(":")) {
            return String.format("%s/%s", namespace, imageRef);
        }
        return imageRef;
    }

    /**
     * Use the OpenShift client to resolve or create an image reference that matches the given input
     * image reference
     *
     * @param client
     * @param imageRef
     * @return
     */
    private String resolveInputRef(final CommandBuild build, final OpenShiftClient client, final String namespace, final String imageRef) {
        // look through tags to find upstream image that matches
        final List<ImageStreamTag> tags = client.imageStreamTags().inAnyNamespace().list().getItems();
        if (!tags.isEmpty()) {
            for (ImageStreamTag tag : tags) {
                if (tag == null || tag.getTag() == null || tag.getTag().getFrom() == null) {
                    continue;
                }

                final String tagFullName = tag.getMetadata().getName();
                final String tagName = tagFullName.split(":")[0];
                final String tagNamespace = tag.getMetadata().getNamespace();

                final String tagNameWithNamespace = String.format("%s/%s", tagNamespace, tagName);
                final String tagFullRef = String.format("%s/%s:%s", tagNamespace, tagName, tag.getTag().getName());
                final String fromName = tag.getTag().getFrom().getName();

                // use tools to get image ref
                if (tagName.equals(imageRef) || tagNameWithNamespace.equals(imageRef) || tagFullRef.equals(imageRef) || fromName.equals(imageRef)) {
                    return tagFullRef;
                }
            }
        }

        // if the name is just 'imageRef' return the image ref on the namespace
        if (!imageRef.contains("/") && !imageRef.contains(":")) {
            return String.format("%s/%s", namespace, imageRef);
        }

        // break down image ref by components which are typically
        // {repo url}/{namespace}/{image name}:{tag}


        return null;
    }

    private void setFromImageRef(final CommandBuild buildCommand, final FileSourceNode node, final OpenShiftClient client, final String namespace, final String from) {
        final String imageRef = this.resolveInputRef(buildCommand, client, namespace, from);
        if (imageRef == null) {
            logger.error("No image reference provided for: '{}'", from);
        } else {
            logger.info("Resolved '{}' for '{}'", imageRef, from);
        }
        final ImageRefSourceNode imageRefSourceNode = new ImageRefSourceNode(imageRef);
        node.setFrom(imageRefSourceNode);

    }
}

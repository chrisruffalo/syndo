package org.ruffalo.syndo.build;

import io.fabric8.openshift.api.model.ImageStreamTag;
import io.fabric8.openshift.client.OpenShiftClient;
import org.ruffalo.syndo.cmd.CommandBuild;
import org.ruffalo.syndo.config.Component;
import org.ruffalo.syndo.model.BuildNode;
import org.ruffalo.syndo.model.DirSourceNode;
import org.ruffalo.syndo.model.DockerfileSourceNode;
import org.ruffalo.syndo.model.ImageRefSourceNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves build components and the build order for those components.
 */
public class BuildResolveAction extends BaseAction {

    @Override
    public void build(BuildContext context) {


        // build everything into the node map
        final Map<String, String> outputRefResolveMap = new HashMap<>();
        final Map<String, DirSourceNode> sourceNodeMap = new HashMap<>();
        context.getComponentMap().forEach((key, component) -> {
            DirSourceNode node = null;
            if (component.getDockerfile() != null && !component.getDockerfile().isEmpty()) {
                final DockerfileSourceNode dockerfileSourceNode = new DockerfileSourceNode(component.getName());
                dockerfileSourceNode.setDockerfile(component.getDockerfile());
                node = dockerfileSourceNode;
            } else {
                node = new DirSourceNode(component.getName());
            }

            // todo: maybe do a better job of resolving the target path
            Path componentDir = Paths.get(component.getPath());
            if (!componentDir.isAbsolute()) {
                if (!Files.exists(componentDir)) {
                    // try and resolve relative to build yaml
                    componentDir = context.getConfigPath().getParent().resolve(componentDir).normalize().toAbsolutePath();
                }
            }
            if (!Files.exists(componentDir)) {
                logger().error("Could not resolve directory '{}' for component '{}', skipping", component.getPath(), component.getName());
                return;
            }
            node.setDirectory(componentDir);

            // todo: do a better job of resolving the output reference
            String to = component.getTo();
            if (to == null || to.isEmpty()) {
                to = component.getName();
            }
            node.setOutputRef(this.resolveOutputRef(context.getCommandBuild(), context.getClient(), context.getNamespace(), to));
            outputRefResolveMap.put(node.getOutputRef(), node.getName());

            // add to map
            sourceNodeMap.put(component.getName(), node);
        });

        // go through again and resolve "from" references
        sourceNodeMap.forEach((key, node) -> {
            final Component component = context.getComponentMap().get(key);
            if (component == null) {
                return;
            }
            final DirSourceNode from = sourceNodeMap.get(component.getFrom());

            if (from == null) {
                // set the dockerfile path on the component using the files in the resolved node directory
                if (node instanceof DockerfileSourceNode) {
                    final DockerfileSourceNode dsNode = (DockerfileSourceNode) node;
                    final Path dockerfilePath = node.getDirectory().resolve(dsNode.getDockerfile()).normalize().toAbsolutePath();
                    if (!Files.exists(dockerfilePath)) {
                        this.logger().error("No dockerfile resolved for '{}' at path '{}'", component.getName(), dockerfilePath);
                    }
                    // re-normalize path
                    dsNode.setDockerfile(node.getDirectory().relativize(dockerfilePath).toString());
                    dsNode.setDockerfile(component.getDockerfile());
                    List<String> dockerLines = null;
                    try {
                        dockerLines = Files.readAllLines(dockerfilePath);
                    } catch (IOException e) {
                        this.logger().error("Could not read dockerfile {}", dockerfilePath);
                    }
                    if (dockerLines == null) {
                        dockerLines = Collections.emptyList();
                    }
                    if (dockerLines.isEmpty()) {
                        this.logger().error("Empty dockerfile {} provided", dockerfilePath);
                    }
                    // this is the raw content
                    dsNode.setDockerfileContents(dockerLines);
                    String fromRef = "";
                    for(final String line : dockerLines) {
                        if (line.trim().toUpperCase().startsWith("FROM")) {
                            fromRef = line.substring(4).trim();
                            break;
                        }
                    }
                    if (fromRef.isEmpty()) {
                        this.logger().error("Could not read FROM image in {}", dockerfilePath);
                    } else {
                        DirSourceNode fromNode = sourceNodeMap.get(fromRef);
                        if (fromNode == null) {
                            final String nodeName = outputRefResolveMap.get(fromRef);
                            fromNode = sourceNodeMap.get(nodeName);
                        }
                        // todo: figure out how to feed the resolved reference back into the docker build file
                        if (fromNode != null) {
                            node.setFrom(fromNode);
                        } else {
                            this.setFromImageRef(context.getCommandBuild(), node, context.getClient(), context.getNamespace(), fromRef);
                        }
                        // update from ref in dockerfile contents
                        for (int i = 0; i < dockerLines.size(); i++) {
                            final String line = dockerLines.get(i);
                            if (line.trim().toUpperCase().startsWith("FROM")) {
                                final String dockerFromRef = String.format("FROM %s", node.getFromRef());
                                dockerLines.set(i, dockerFromRef);
                                dsNode.setDockerfileContents(dockerLines);
                                break;
                            }
                        }
                    }
                } else {
                    this.setFromImageRef(context.getCommandBuild(), node, context.getClient(), context.getNamespace(), component.getFrom());
                }
            } else {
                node.setFrom(from);
                // keep the from node
                from.setKeep(true);
            }
        });
        context.setNodeMap(sourceNodeMap);

        // todo: check for cyclic dependencies

        // ensure that the build list is the nodes, in order
        final List<DirSourceNode> buildOrder = new LinkedList<>();
        while(!sourceNodeMap.isEmpty()) {
            final Set<Map.Entry<String, DirSourceNode>> entrySet = new HashSet<>(sourceNodeMap.entrySet());
            for (Map.Entry<String, DirSourceNode> entry : entrySet) {
                final DirSourceNode node = entry.getValue();
                final BuildNode from = node.getFrom();
                // if it starts from an image reference it can go instantly
                if (from instanceof ImageRefSourceNode) {
                    buildOrder.add(node);
                    sourceNodeMap.remove(node.getName());
                    continue;
                }

                // if the parent file source node has been removed from the map
                // then this node is cleared to go
                if (from instanceof DirSourceNode) {
                    final DirSourceNode fromFileSource = (DirSourceNode) from;
                    if (sourceNodeMap.get(fromFileSource.getName()) == null) {
                        buildOrder.add(node);
                        sourceNodeMap.remove(node.getName());
                        continue;
                    }
                }

                // do nothing
            }
        }

        // set build order on context result
        context.setBuildOrder(buildOrder);
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

    private void setFromImageRef(final CommandBuild buildCommand, final DirSourceNode node, final OpenShiftClient client, final String namespace, final String from) {
        final String imageRef = this.resolveInputRef(buildCommand, client, namespace, from);
        if (imageRef == null) {
            this.logger().error("No image reference provided for: '{}'", from);
        } else {
            this.logger().info("Resolved '{}' for '{}'", imageRef, from);
        }
        final ImageRefSourceNode imageRefSourceNode = new ImageRefSourceNode(imageRef);
        node.setFrom(imageRefSourceNode);

    }
}

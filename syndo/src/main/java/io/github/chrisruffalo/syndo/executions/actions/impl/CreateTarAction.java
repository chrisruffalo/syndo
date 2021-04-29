package io.github.chrisruffalo.syndo.executions.actions.impl;

import io.github.chrisruffalo.syndo.executions.actions.BaseAction;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.model.DirSourceNode;
import io.github.chrisruffalo.syndo.model.DockerfileSourceNode;
import io.github.chrisruffalo.syndo.model.ImageRefSourceNode;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import io.github.chrisruffalo.syndo.resources.TarCreator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateTarAction extends BaseAction {

    @Override
    public void build(BuildContext context) {
        final Path outputTar = context.getOutputTar();
        try (final TarArchiveOutputStream tarStream = TarCreator.createTar(outputTar)) {
            final List<DirSourceNode> buildOrder = context.getBuildOrder();
            // walk through the build components, add them to the tar, and then add metadata to them
            // we do this with an indexed for loop so we can get the build order built out in a way
            // that bash will understand
            for (int i = 0; i < buildOrder.size(); i++) {
                final DirSourceNode sourceNode = buildOrder.get(i);

                final Path dirNodeDir = sourceNode.getDirectory();
                final String prefix = String.format("%04d_%s", i, sourceNode.getName());
                logger().info("Adding {} with context {} to {}", sourceNode.getName(), dirNodeDir, prefix);

                // now add metadata to prefix
                final Map<String, String> meta = new HashMap<>();
                meta.put("COMPONENT", sourceNode.getName());
                meta.put("OUTPUT_TARGET", sourceNode.getOutputRef());
                meta.put("HASH", sourceNode.getHash());
                meta.put("BUILD_SCRIPT", sourceNode.getScript());

                if(sourceNode instanceof DockerfileSourceNode) {
                    meta.put("DOCKERFILE", ((DockerfileSourceNode)sourceNode).getDockerfile());
                }

                // handle the from image and the RESOLVED property. the RESOLVED property means
                // that the image was found "in cluster" and will need to be prefixed with the
                // cluster's internal registry url.
                final String fromRef = sourceNode.getFromRef();
                if (fromRef != null) {
                    meta.put("FROM_IMAGE", fromRef);
                    if (sourceNode.getFrom() instanceof ImageRefSourceNode) {
                        if (((ImageRefSourceNode) sourceNode.getFrom()).isResolvedInternally()) {
                            // resolved here means that the tag matching the input was found in
                            // the cluster itself
                            meta.put("RESOLVED", "true");
                        }
                    } else if (sourceNode.getFrom() instanceof DirSourceNode) {
                        // dir sources are always resolved (internal to the cluster) because they
                        // are built and inserted there
                        meta.put("RESOLVED", "true");
                    }
                }

                // hardcode storage preference if "vfs" selected
                if ("vfs".equalsIgnoreCase(sourceNode.getStorage())) {
                    meta.put("STORAGE_DRIVER", "vfs");
                }

                // don't delete the output image if the output image is used elsewhere in the build
                if (sourceNode.isKeep()) {
                    meta.put("KEEP", "true");
                }

                // then meta files
                TarCreator.addMetaEnvToTar(tarStream, prefix + "/.meta/env", meta);

                // add project contexts tar, excluding files that may have been previously created
                TarCreator.addPrefixedDirectoryToTar(tarStream, sourceNode.getDirectory(), prefix);
            }
        } catch (IOException e) {
            this.logger().error("Could not create tar output stream for build", e);
            context.setStatus(BuildContext.Status.ERROR);
        }
    }
}

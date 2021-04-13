package org.ruffalo.syndo.build;

import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.ruffalo.syndo.model.DirSourceNode;
import org.ruffalo.syndo.model.DockerfileSourceNode;
import org.ruffalo.syndo.resources.TarCreator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

                if(sourceNode instanceof DockerfileSourceNode) {
                    meta.put("DOCKERFILE", ((DockerfileSourceNode)sourceNode).getDockerfile());
                }

                final String fromRef = sourceNode.getFromRef();
                if (fromRef != null) {
                    meta.put("FROM_IMAGE", fromRef);
                }

                if (sourceNode.isKeep()) {
                    meta.put("KEEP", "true");
                }

                final Set<String> excludes = new HashSet<>();

                // then meta files
                TarCreator.addMetaEnvToTar(tarStream, prefix + "/.meta/env", meta);

                // add project contexts tar, excluding files that may have been previously created
                TarCreator.addPrefixedDirectoryToTar(tarStream, sourceNode.getDirectory(), prefix, excludes);
            }
        } catch (IOException e) {
            this.logger().error("Could not create tar output stream for build", e);
            context.setStatus(BuildContext.Status.ERROR);
        }
    }
}

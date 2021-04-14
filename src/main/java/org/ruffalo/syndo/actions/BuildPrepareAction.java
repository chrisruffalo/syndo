package org.ruffalo.syndo.actions;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.ruffalo.syndo.config.Component;
import org.ruffalo.syndo.config.Root;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the inputs and outputs of build images and creates the correct build order.
 *
 */
public class BuildPrepareAction extends BaseAction {

    @Override
    public void build(BuildContext context) {
        final Root config = context.getConfig();

        // create map of components so we can handle ordering/dependencies
        final Map<String, Component> componentMap = new HashMap<>();
        config.getComponents().forEach(component -> componentMap.put(component.getName(), component));
        context.setComponentMap(componentMap);

        // create output build tar
        Path outputTar = context.getCommandBuild().getTarOutput();
        if (outputTar != null) {
            outputTar = outputTar.normalize().toAbsolutePath();
            this.logger().info("Build tar: {}", outputTar);
        } else {
            final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
            outputTar = fs.getPath("build-output.tar.gz").normalize().toAbsolutePath();
        }
        context.setOutputTar(outputTar);
    }

}

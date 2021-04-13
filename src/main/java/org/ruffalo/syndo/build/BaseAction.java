package org.ruffalo.syndo.build;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import java.nio.file.FileSystem;

public abstract class BaseAction implements Action {

    private static final FileSystem memorySystem = Jimfs.newFileSystem(Configuration.unix());


    protected FileSystem fs() {
        return memorySystem;
    }

}

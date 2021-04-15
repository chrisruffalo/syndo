package org.ruffalo.syndo.executions.actions;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileSystem;

public abstract class BaseAction implements Action {

    private static final FileSystem memorySystem = Jimfs.newFileSystem(Configuration.unix());

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected FileSystem fs() {
        return memorySystem;
    }

    protected Logger logger() {
        return this.logger;
    }

}

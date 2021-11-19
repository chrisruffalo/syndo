package io.github.chrisruffalo.syndo.executions.actions.log;

import io.fabric8.kubernetes.client.dsl.LogWatch;

import java.io.*;

public abstract class LogFollower implements Runnable {

    /**
     * Lines that start with the log prefix are considered "special" and are intended for
     * outside consumption. This is not true of the full log follower which has special
     * processing logic to remove them.
     */
    public static final String LOG_PREFIX = "##== LOG ==##";

    /**
     * This reports build status back to the log follower. This allows the logger to know when
     * milestones have been accomplished in the build without necessarily making them visible.
     */
    public static final String STATUS_PREFIX = "##== STATUS ==##";

    /**
     * Errors are reported back with the error prefix. Each follower might have a different
     * way of handling errors.
     */
    protected static final String ERROR_PREFIX = "##== ERROR ==##";

    private final LogWatch logWatch;

    public LogFollower(final LogWatch logWatch) {
        this.logWatch = logWatch;
    }

    protected LogWatch getLogWatch() {
        return this.logWatch;
    }

    protected abstract void processLine(final String line);

    protected abstract void processException(final Exception ex);

    @Override
    public void run() {
        try (
            final LogWatch watch = this.getLogWatch();
            final InputStream stream = watch.getOutput();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(stream)));
        ) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                this.processLine(line);
            }
        } catch (IOException e) {
            // nothing we can do about it but send the line to be processed
            this.processException(e);
        }
    }
}

package com.red5pro.server.stream;

import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.concurrent.ThreadFactory;

import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class Red5ProThreadFactory implements ThreadFactory {

    private static Logger log = Red5LoggerFactory.getLogger(Red5ProThreadFactory.class);

    private final String name;

    private final int priority;

    // args for the string formatter if in-use
    private Object[] args;

    // whether or not to apply a variable suffix to the formatted name
    private boolean useSuffix;

    public Red5ProThreadFactory() {
        // default ctor
        name = "Red5ProThread";
        priority = Thread.NORM_PRIORITY;
    }

    public Red5ProThreadFactory(String name) {
        this.name = name;
        priority = Thread.NORM_PRIORITY;
    }

    public Red5ProThreadFactory(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }

    public Red5ProThreadFactory(String name, Object... args) {
        this.name = name; // technically this is the string format
        priority = Thread.NORM_PRIORITY;
        this.args = args;
    }

    public Red5ProThreadFactory(boolean useSuffix, String name, Object... args) {
        this.name = name; // technically this is the string format
        priority = Thread.NORM_PRIORITY;
        this.args = args;
        this.useSuffix = useSuffix;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        // set as a daemon
        t.setDaemon(true);
        // a helpful name
        if (args == null) {
            t.setName(name);
        } else {
            String threadName = String.format(name, args);
            if (useSuffix) {
                long suffix = Instant.now().getLong(ChronoField.MILLI_OF_SECOND);
                t.setName(threadName + "-" + suffix);
            } else {
                t.setName(threadName);
            }
        }
        // set the priority
        t.setPriority(priority);
        // set a default handler for exceptions
        t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread t, Throwable e) {
                log.warn("Uncaught exception on {}", t.getName(), e);
                // if its something that will kill our checker thread, make sure it respawns
                if (e instanceof ThreadDeath) {
                    log.error("Thread died");
                }
            }

        });
        return t;
    }

}

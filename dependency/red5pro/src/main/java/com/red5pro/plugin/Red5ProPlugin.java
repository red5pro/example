package com.red5pro.plugin;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.red5.net.websocket.WebSocketConnection;
import org.red5.server.api.IConnection;
import org.red5.server.api.scope.IScope;
import org.red5.server.plugin.Red5Plugin;
import org.red5.server.scope.ScopeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import com.google.gson.JsonObject;
import com.red5pro.media.rtp.RTPCodecEnum;
import com.red5pro.server.event.ServerEvent;
import com.red5pro.server.event.ServerEventListener;
import com.red5pro.server.stream.Red5ProThreadFactory;
import com.red5pro.service.IRed5ProService;
import com.red5pro.servlet.filter.CorsConfig;

/**
 * Represents the base class for all Red5 Pro plugins.
 * <em>THIS IS A STUB FOR COMPILATION ONLY</em>
 *
 * @author Dominick Accattato
 * @author Paul Gregoire
 */
public abstract class Red5ProPlugin extends Red5Plugin {

    private static Logger log = LoggerFactory.getLogger(Red5ProPlugin.class);

    // ready state flag
    protected static boolean ready;

    // event listeners - ideally for internal events between plugins
    protected static ConcurrentSkipListSet<ServerEventListener> eventListeners = new ConcurrentSkipListSet<>();

    // size of the scheduled pool (per plugin). Previously used "availableCPUs * 2" which was too large on some multicore boxes
    private static int scheduledPoolSize = Math.min(16, (Runtime.getRuntime().availableProcessors() * 2)); // picks the smaller of the two so we don't over-allocate

    // Stream name illegal character filter regex, for publishers
    public static String safeStreamRegEx = "[=+^:,/]";

    // collect the arch and name just once and reuse x times
    protected static final String ao = System.getProperty("os.arch") + "-" + System.getProperty("os.name").replaceAll(" ", "");

    public static final boolean isLinux = ao.contains("Linux");

    public static final boolean isWin = ao.contains("indows");

    public static final boolean isOsx = ao.contains("Mac");

    // whether or not to transcode to ProStream depends on ingest codec and the list of supported codecs
    public static EnumSet<RTPCodecEnum> supportedAudioCodecs = EnumSet.of(RTPCodecEnum.AAC),
            supportedVideoCodecs = EnumSet.of(RTPCodecEnum.H264);

    // maximum amount of time alloted for creation of pub/sub entity
    protected static long maxStreamCreationMs = 5000L;

    /**
     * Default h264 profile used for RTC streaming.
     */
    protected static String profile = "42e01f";

    /**
     * Network properties for all plugins.
     */
    protected static Properties networkProps = new Properties();

    /**
     * Local network server flag.
     */
    protected static String localNetworkAddress;

    static {
        // this should be extracted to config at some point
        WebSocketConnection.setUseAsync(false);
        // load network properties from file just in case a plugin needs them
        try {
            networkProps.load(Files.newInputStream(Paths.get("conf/network.properties")));
            // set when running containers on a local network
            localNetworkAddress = networkProps.getProperty("local.network.address");
        } catch (IOException e) {
            log.warn("Could not load network properties", e);
        }
    }

    /**
     * Configure the supported audio codecs with AMF encapsulation for the ProStream.
     *
     * @param supportedAudioCodecs
     */
    public static void setProStreamAudioCodecs(Set<String> supportedAudioCodecs) {
        Red5ProPlugin.supportedAudioCodecs.clear();
        for (String codec : supportedAudioCodecs) {
            RTPCodecEnum codecEnum = RTPCodecEnum.valueOf(codec);
            if (codecEnum != null) {
                Red5ProPlugin.supportedAudioCodecs.add(codecEnum);
            }
        }
    }

    public static EnumSet<RTPCodecEnum> getProStreamAudioCodecs() {
        return supportedAudioCodecs;
    }

    /**
     * Configure the supported video codecs with AMF encapsulation for the ProStream.
     *
     * @param supportedVideoCodecs
     */
    public static void setProStreamVideoCodecs(Set<String> supportedVideoCodecs) {
        Red5ProPlugin.supportedVideoCodecs.clear();
        for (String codec : supportedVideoCodecs) {
            RTPCodecEnum codecEnum = RTPCodecEnum.valueOf(codec);
            if (codecEnum != null) {
                Red5ProPlugin.supportedVideoCodecs.add(codecEnum);
            }
        }
    }

    public static EnumSet<RTPCodecEnum> getProStreamVideoCodecs() {
        return supportedVideoCodecs;
    }

    public static boolean FeatureOpusAudio() {
        return true;
    }

    public static Object[] getConnectParams(IConnection conn) {
        return new Object[0];
    }

    // scope resolver for use by plugins, set up with single global scope at startup; if global scopes increase we'll need to address it
    protected static ScopeResolver scopeResolver;

    // Per-instance executor for any / all general tasks
    protected ExecutorService executor = Executors.newCachedThreadPool(new Red5ProThreadFactory(true, "%s", getName()));

    // Per-instance single threaded executor utilizing an unbounded work queue
    protected ExecutorService queuedExecutor = Executors.newSingleThreadExecutor(new Red5ProThreadFactory(true, "%s-que", getName()));

    // Per-instance scheduled executor for scheduled tasks
    protected ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(scheduledPoolSize,
            new Red5ProThreadFactory(true, "%s-sched", getName()));

    public abstract void doStartProPlugin(FileSystemXmlApplicationContext configContext) throws IOException;

    /**
     * Called by ProPluginator.doStop() to provide clean shutdown of plugins.
     *
     * @throws Exception
     */
    public void doStopProPlugin() throws Exception {
        // shutdown the executor
        if (executor != null && !executor.isTerminated()) {
            try {
                // stop the media threads
                executor.shutdown();
                // wait a few moments for termination
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (Throwable t) {
                log.debug("Exception stopping executor", t);
            }
        }
        // shutdown the queued executor
        if (queuedExecutor != null && !queuedExecutor.isTerminated()) {
            try {
                // stop the media threads
                queuedExecutor.shutdown();
                // wait a few moments for termination
                if (!queuedExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    queuedExecutor.shutdownNow();
                }
            } catch (Throwable t) {
                log.debug("Exception stopping queuedExecutor", t);
            }
        }
        // shutdown scheduled executor
        if (scheduledExecutor != null && !scheduledExecutor.isTerminated()) {
            try {
                // stop the media threads
                scheduledExecutor.shutdown();
                // wait a few moments for termination
                if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (Throwable t) {
                log.debug("Exception stopping scheduledExecutor", t);
            }
        }
    }

    /**
     * Returns the ready state of the Pro server instance.
     *
     * @return true if ready and false otherwise
     */
    public static boolean isReady() {
        return ready;
    }

    /**
     * Provides a means to share execution of similar tasks and return the Future.
     *
     * @param task Runnable task
     * @return Future
     */
    public Future<?> submitTask(Runnable task) {
        return executor.submit(task);
    }

    /**
     * Provides a means to share scheduled execution of similar tasks. Time unit default is in seconds.
     *
     * @param task Runnable task for scheduling
     * @param initialDelay Initial delay in seconds before first execution
     * @param delay Successive delay after first run
     * @return Future
     */
    public Future<?> scheduleTask(Runnable task, long initialDelay, long delay) {
        log.debug("Submit scheduled task");
        return submitScheduledTask(task, (initialDelay >= 1000L ? initialDelay : (initialDelay * 1000L)),
                (delay >= 1000L ? delay : (delay * 1000L)));
    }

    /**
     * Submit a task for scheduled execution.
     *
     * @param task
     * @param initialDelay initial delay in milliseconds
     * @param rerunDelay re-run delay in milliseconds after run completes
     * @return Future<?>
     */
    public Future<?> submitScheduledTask(final Runnable task, final long initialDelay, final long rerunDelay) {
        log.debug("submitScheduledTask: {} delay: {} rerun: {}", task, initialDelay, rerunDelay);
        if (rerunDelay == 0) {
            Callable<Future<?>> delayedTask = new Callable<Future<?>>() {

                Future<?> future = null;

                @Override
                public Future<?> call() throws Exception {
                    try {
                        Thread.sleep(initialDelay);
                        // now send original task to be executed with the non-scheduled executor
                        future = executor.submit(task);
                    } catch (Exception e) {
                        log.warn("Exception scheduling task", e);
                    }
                    return future;
                }

            };
            return executor.submit(delayedTask);
        }
        // schedule the task, re-run delay is the period between one completed run and another
        return scheduledExecutor.scheduleWithFixedDelay(task, initialDelay, rerunDelay, TimeUnit.MILLISECONDS);
    }

    public Future<?> submitCallable(Callable<?> task) {
        return scheduledExecutor.submit(task);
    }

    /**
     * Returns the size of the executor task queue.
     *
     * @return executor task queue size
     */
    public int getThreadQueueSize() {
        return (executor != null && executor instanceof ThreadPoolExecutor) ? ((ThreadPoolExecutor) executor).getQueue().size() : 0;
    }

    /**
     * Provides a means to share execution of similar tasks and return the Future. The tasks submitted are placed
     * in an unbounded queue and executed one-at-a-time.
     *
     * @param task Runnable task
     * @return Future
     */
    public Future<?> submitQueuedTask(Runnable task) {
        return queuedExecutor.submit(task);
    }

    /**
     * Returns a IPluginService give an identifying string.
     *
     * @param serviceId service identifier
     * @return IPluginService matching the identifier or null if not found
     */
    public IPluginService getService(String serviceId) {
        return null;
    }

    /**
     * Returns a property matching the given key.
     *
     * @param key the key
     * @return value or null if no matching entry is available
     */
    public String getProperty(String key) {
        // no props by default here
        return null;
    }

    /**
     * Returns a property for the given key and if not found, the default value is returned.
     *
     * @param key the key
     * @param defaultValue a default value
     * @return value or default value if no matching entry is available
     */
    public String getProperty(String key, String defaultValue) {
        return defaultValue;
    }

    /**
     * Returns a network specific string property.
     *
     * @param key
     * @param defaultValue
     * @return value or null
     */
    public static String getNetworkProperty(String key, String defaultValue) {
        return networkProps.getProperty(key, defaultValue);
    }

    /**
     * Returns the named bean in the plugin's context.
     *
     * @param beanId identifier for the bean requested
     * @return bean matching the beanId or null if not found
     */
    public Object getBean(String beanId) {
        // spring context isn't stored in super at this time
        return null;
    }

    /**
     * Returns a value from the jar's manifest for a given key.
     *
     * @param key
     * @return value associated with the key or null if not found
     */
    public String getManifestValue(String key) {
        String value = "undefined";
        try {
            Class<?> self = this.getClass();
            URL location = self.getResource(String.format("/%s.class", self.getName().replace('.', '/')));
            // file:/usr/share/red5/plugins/red5pro-pluginator-1.0.5-SNAPSHOT.jar!/com/red5pro/activation/ProPluginator.class
            log.trace("Resource path: {}", location.getPath());
            String jarPath = location.getPath().split("!")[0];
            if (jarPath.startsWith("file:")) {
                jarPath = jarPath.substring(jarPath.indexOf(':') + 1);
            }
            log.trace("Jar path: {}", jarPath);
            // skip the error for non-jar executions (such as in junit)
            if (jarPath.contains(".jar")) {
                JarFile jar = null;
                Manifest manifest = null;
                try {
                    jar = new JarFile(jarPath, false);
                    manifest = jar.getManifest();
                    Attributes attributes = manifest.getMainAttributes();
                    if (attributes != null) {
                        value = attributes.getValue(key);
                    }
                } catch (Exception ex) {
                    log.warn("Error loading plugin manifest", ex);
                } finally {
                    if (jar != null) {
                        try {
                            jar.close();
                        } catch (IOException e) {
                            // no-op
                        }
                    }
                }
            } else {
                log.debug("Manifest not available from non-jar file");
            }
        } catch (Throwable t) {
            log.warn("Error getting manifest value", t);
        }
        return value;
    }

    /**
     * Returns registered plugin services.
     *
     * @return set of IPluginService or null if none are registered
     */
    public Set<IPluginService> getServices() {
        return null;
    }

    public static int getScheduledPoolSize() {
        return scheduledPoolSize;
    }

    public static void setScheduledPoolSize(int scheduledPoolSize) {
        Red5ProPlugin.scheduledPoolSize = scheduledPoolSize;
    }

    public static long getMaxStreamCreationMs() {
        return maxStreamCreationMs;
    }

    public static void setMaxStreamCreationMs(long maxStreamCreationMs) {
        Red5ProPlugin.maxStreamCreationMs = maxStreamCreationMs;
    }

    public static CorsConfig getCorsConfig() {
        return null;
    }

    /**
     * Returns the ICE connection timeout.
     *
     * @return ICE connection timeout
     */
    public static long getIceConnectTimeout() {
        return Integer.valueOf(networkProps.getProperty("ice.connect.timeout", "3000"));
    }

    /**
     * Gather resource information about the current JVM.
     *
     * @return JsonObject
     */
    public static JsonObject ResourceCall() {
        return null;
    }

    /**
     * Normalizes the context path into a usable string. It strips the leading '/' and adds a trailing '/'.
     *
     * @param contextPath
     * @return String
     */
    public static final String normalizeContextPath(String contextPath) {
        StringBuilder sb = new StringBuilder(contextPath);
        if (sb.charAt(0) == '/') {
            sb.deleteCharAt(0);
        }
        if (sb.charAt(sb.length() - 1) != '/') {
            sb.append('/');
        }
        return sb.toString();
    }

    /**
     * Normalizes the context path into a usable string. It strips the leading '/' and adds a trailing '/'. Lastly adding the
     * stream name at the end. Be aware that a null context path or stream name is converted to an empty string.
     *
     * @param contextPath
     * @param streamName
     * @return String
     */
    public static final String normalizeContextPath(String contextPath, String streamName) {
        log.debug("normalizeContextPath - path: {} streamName: {}", contextPath, streamName);
        // handle possible null value for context path
        StringBuilder sb = new StringBuilder(contextPath != null ? contextPath : "");
        if (sb.charAt(0) == '/') {
            sb.deleteCharAt(0);
        }
        if (sb.charAt(sb.length() - 1) != '/') {
            sb.append('/');
        }
        // handle possible null for stream name
        sb.append(streamName != null ? streamName : "");
        return sb.toString();
    }

    public static final String sanitize(String path, String name) {
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.endsWith("/")) {
            path = path.concat("/");
        }
        return path.concat(name);
    }

    /**
     * Dispatch a server event to interested listeners. The listeners list is static for shared access between plugin
     * implementations, but dispatching is per plugin instance.
     *
     * @param evt
     */
    public void dispatchServerEvent(final PropertyChangeEvent evt) {
        log.info("Dispatch server event: {}", evt);
        // determine the event type for filtering to those interested in it
        final ServerEvent eventType = ServerEvent.valueOf(evt.getPropertyName());
        // fire-off a worker to do the event propagation filtered by event type
        queuedExecutor.submit(() -> eventListeners.stream().filter(listener -> listener.hasInterest(eventType))
                .forEach(listener -> listener.propertyChange(evt)));
    }

    /**
     * Force a server event dispatch to all listeners. This is a synchronous call and should be used with caution.
     *
     * @param evt
     */
    public static final void dispatchServerEventInline(final PropertyChangeEvent evt) {
        try {
            eventListeners.forEach(listener -> listener.propertyChange(evt));
        } catch (Exception e) {
            log.warn("Exception dispatching: {}", evt, e);
        }
    }

    /**
     * Register a server event listener.
     *
     * @param serverEventListener
     * @return true if added and false otherwise
     */
    public static boolean addServerEventListener(ServerEventListener serverEventListener) {
        return eventListeners.add(serverEventListener);
    }

    /**
     * Removes a server event listener.
     *
     * @param serverEventListener
     * @return true if removed and false otherwise
     */
    public static boolean removeServerEventListener(ServerEventListener serverEventListener) {
        return eventListeners.remove(serverEventListener);
    }

    /**
     * Removes a server event listener by its owner, since owners may not keep a reference to the listener.
     *
     * @param owner
     * @return true if removed and false otherwise
     */
    public static boolean removeServerEventListenerByOwner(Object owner) {
        return eventListeners.removeIf(listener -> (listener.getOwner().equals(owner)));
    }

    /**
     * Register a service.
     *
     * @param serviceName
     * @param instance
     * @return true if registration is successful and false otherwise
     */
    public static boolean registerProService(String serviceName, IRed5ProService instance) {
        return false;
    }

    /**
     * Removes a service registration by its name.
     *
     * @param serviceName
     * @return true if removed and false otherwise
     */
    public static boolean unregisterProService(String serviceName) {
        return false;
    }

    /**
     * Returns a service matching the service name or null if there is no match.
     *
     * @param serviceName
     * @return IRed5ProService or null if not found
     */
    public static IRed5ProService getProService(String serviceName) {
        return null;
    }

    public static String getDefaultProfile() {
        return profile;
    }

    public static void setDefaultProfile(String profile) {
        Red5ProPlugin.profile = profile;
    }

    /**
     * Resolves a scope for a given path.
     *
     * @param path
     * @return scope if found and null if not found
     */
    public static IScope resolveScope(String path) {
        return scopeResolver.resolveScope(path);
    }

    /**
     * Returns this servers local network (LAN) address. This is used for containerized deployments where the server is
     * handling requests which would normally be blocked on the public address.
     *
     * @return local network address or null if not set
     */
    public static String getLANAddress() {
        return localNetworkAddress;
    }

}

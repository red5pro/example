package example;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.core.io.Resource;
import org.red5.server.plugin.PluginRegistry;
import com.red5pro.plugin.Red5ProPlugin;
import com.red5pro.server.event.ServerEvent;

public class Red5ProTemplatePlugin extends Red5ProPlugin {

    private Logger log = LoggerFactory.getLogger(Red5ProTemplatePlugin.class);

    public static final String NAME = "red5pro-template-plugin";

    private static String pluginPropsFileName = NAME + ".properties";

    private FileSystemXmlApplicationContext appContext;

    private Properties appProps = new Properties();

    @Override
    public String getName() {
        return Red5ProTemplatePlugin.NAME;
    }

    @Override
    public void doStartProPlugin(FileSystemXmlApplicationContext configContext) throws IOException {
        log.info("doStartProPlugin called");
        // register the plug-in to make it available for lookups
        PluginRegistry.register(this);
        // dispatch start event
        dispatchServerEvent(ServerEvent.build(ServerEvent.PLUGIN_START, this, NAME));
        // spawn a new thread that will wait for the server to be ready before starting the plugin
        executor.execute(() -> {
            Thread.currentThread().setName("Red5ProTemplatePlugin-Startup");
            while (!Red5ProPlugin.isReady()) {
                try {
                    Thread.sleep(100L); // adjust sleep time as needed
                } catch (InterruptedException e) {
                    log.warn("Error waiting for server to be ready", e);
                }
            }
            // perform plugin startup tasks here, starting with the application context and properties
            try {
                // create plugins application context checking jar and then conf directory
                appContext = new FileSystemXmlApplicationContext(new String[] { "classpath:/red5pro-template-plugin.xml" }, context);
                // set the properties
                InputStream in = null;
                // attempt load from conf directory first to match pro style
                Resource res = appContext.getResource("classpath:/conf/" + pluginPropsFileName);
                if (!res.exists()) {
                    // attempts load from inside the classpath
                    res = appContext.getResource("classpath:/" + pluginPropsFileName);
                    if (!res.exists()) {
                        // load from the jar
                        in = getClass().getResourceAsStream("/" + pluginPropsFileName);
                        if (in == null) {
                            log.warn("Properties not found in classloader!");
                        }
                    } else {
                        in = res.getInputStream();
                    }
                } else {
                    in = res.getInputStream();
                }
                appProps.load(in);
            } catch (Exception e) {
                log.error("Error on start", e);
            }
            // TODO perform any other startup tasks here

        });
    }

    @Override
    public void doStopProPlugin() throws Exception {
        log.info("doStopProPlugin called");
        // dispatch stop event
        dispatchServerEvent(ServerEvent.build(ServerEvent.PLUGIN_STOP, this, NAME));
        // unregister the plug-in
        PluginRegistry.unregister(this);
        // TODO perform any other cleanup tasks here

        // calling into super stops the executors
        super.doStopProPlugin();
        // close the context
        if (appContext != null) {
            ((ConfigurableApplicationContext) appContext).close();
        }
    }

    @Override
    public Object getBean(String beanId) {
        return appContext.getBean(beanId);
    }

    @Override
    public String getProperty(String key) {
        return appProps.getProperty(key);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return appProps.getProperty(key, defaultValue);
    }

    /**
     * Submits a runnable task to the plugin instance executor.
     *
     * @param task
     * @return Future
     */
    public static Future<?> submit(Runnable task) {
        return ((Red5ProTemplatePlugin) PluginRegistry.getPlugin(Red5ProTemplatePlugin.NAME)).submitTask(task);
    }

    public static Future<?> submit(Callable<?> task) {
        return ((Red5ProTemplatePlugin) PluginRegistry.getPlugin(Red5ProTemplatePlugin.NAME)).submitCallable(task);
    }

    /**
     * Submits a scheduled task to the plugin instance executor.
     *
     * @param task Runnable task for scheduling
     * @param initialDelay Initial delay in seconds before first execution
     * @param delay Successive delay after first run
     * @return Future
     */
    public static Future<?> schedule(Runnable task, long initialDelay, long delay) {
        return ((Red5ProTemplatePlugin) PluginRegistry.getPlugin(Red5ProTemplatePlugin.NAME)).scheduleTask(task, initialDelay, delay);
    }

}

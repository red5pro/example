package example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.red5.server.plugin.Red5Plugin;

public class Red5TemplatePlugin extends Red5Plugin {

    private Logger log = LoggerFactory.getLogger(Red5TemplatePlugin.class);

    public static final String NAME = "red5-template-plugin";

    @Override
    public String getName() {
        return Red5TemplatePlugin.NAME;
    }

    @Override
    public void doStart() throws Exception {
        log.info("doStart called");
    }

    @Override
    public void doStop() throws Exception {
        log.info("doStop called");
    }

}

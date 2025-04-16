package io.github.firstred.iptvproxy;

import java.io.File;

import io.github.firstred.iptvproxy.config.IptvProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        try {
            LOG.info("loading config...");

            File configFile = new File(System.getProperty("config", "config.yml"));

            IptvProxyConfig config = ConfigLoader.loadConfig(configFile, IptvProxyConfig.class);

            IptvProxyService service = new IptvProxyService(config);

            Runtime.getRuntime().addShutdownHook(new Thread(service::stopService));
            service.startService();
        } catch (Exception e) {
            LOG.error("fatal error", e);
            System.exit(1);
        }
    }
}

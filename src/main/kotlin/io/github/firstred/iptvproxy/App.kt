package io.github.firstred.iptvproxy

import io.github.firstred.iptvproxy.config.IptvProxyConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

object App {
    private val LOG: Logger = LoggerFactory.getLogger(App::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            LOG.info("loading config...")

            val configFile = File(System.getProperty("config", "config.yml"))

            val config = ConfigLoader.loadConfig(configFile, IptvProxyConfig::class.java)

            val service = IptvProxyService(config!!)

            Runtime.getRuntime().addShutdownHook(Thread { service.stopService() })
            service.startService()
        } catch (e: Exception) {
            LOG.error("fatal error", e)
            System.exit(1)
        }
    }
}

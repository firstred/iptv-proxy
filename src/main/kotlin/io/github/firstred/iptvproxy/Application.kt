package io.github.firstred.iptvproxy

import com.charleskorn.kaml.InvalidPropertyValueException
import com.charleskorn.kaml.YamlException
import com.github.ajalt.clikt.core.CliktCommand
import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import io.github.firstred.iptvproxy.di.modules.appModule
import io.github.firstred.iptvproxy.dtos.config.IptvProxyConfig
import io.github.firstred.iptvproxy.plugins.appMicrometerRegistry
import io.github.firstred.iptvproxy.plugins.configureDatabase
import io.github.firstred.iptvproxy.plugins.configureRouting
import io.github.firstred.iptvproxy.plugins.installHealthCheckRoute
import io.github.firstred.iptvproxy.plugins.installMetricsRoute
import io.github.firstred.iptvproxy.plugins.startLifecycleHooks
import io.github.firstred.iptvproxy.serialization.json
import io.github.firstred.iptvproxy.serialization.yaml
import io.github.firstred.iptvproxy.utils.ktor.defaultCallLoggingFormat
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.sentry.Sentry
import org.apache.commons.text.StringSubstitutor
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File
import java.net.Authenticator
import java.net.PasswordAuthentication
import kotlin.system.exitProcess

var argv = emptyArray<String>()

lateinit var config: IptvProxyConfig
lateinit var dotenv: Dotenv

private val LOG: Logger = LoggerFactory.getLogger(Application::class.java)


fun main(args: Array<String>) {
    try {
        argv = args
        dotenv = dotenv { ignoreIfMissing = true }
        val sentryDsn = dotenv.get("SENTRY_DSN") ?: ""
        if (sentryDsn.isNotEmpty()) Sentry.init { options ->
            options.dsn = sentryDsn
            options.release = config.sentry?.release ?: dotenv.get("SENTRY_RELEASE")
            options.isDebug = config.sentry?.debug ?: dotenv.get("SENTRY_DEBUG").toBoolean()
        }

        object : CliktCommand(printHelpOnEmptyArgs = false) {
            override fun run() {
                // TODO: make configuration location configurable
                try {
                    LOG.info("Loading config...")
                    config = loadConfig(File(System.getProperty("config", "config.yml")))
                } catch (e: InvalidPropertyValueException) {
                    LOG.error("Invalid property `${e.propertyName}` in config file: ${e.reason}")
                    exitProcess(1)
                } catch (e: YamlException) {
                    LOG.error("Error parsing config file: ${e.message}")
                    exitProcess(1)
                }

                startServer()
            }
        }
            .main(args)
    } catch (e: Exception) {
        LOG.error("fatal error", e)
        exitProcess(1)
    }
}

private fun startServer() {
    embeddedServer(
        CIO,
        serverConfig {
            developmentMode = true

            module {
                install(Koin) {
                    slf4jLogger()
                    modules(appModule)
                }
                configureDatabase()

                install(AutoHeadResponse)
                install(CallLogging) {
                    logger = LOG
                    level = Level.INFO

                    defaultCallLoggingFormat()
                }
                install(MicrometerMetrics) {
                    registry = appMicrometerRegistry
                }
                if (config.compressResponses) install(Compression) {
                    gzip { priority = .5 }
                    deflate { priority = .3 }
                }
                if (config.cors.enabled) install(CORS) {
                    allowCredentials = config.cors.allowCredentials
                    allowNonSimpleContentTypes = true
                    config.cors.allowHeaders.forEach { allowHeader(it) }
                    config.cors.allowHeaderPrefixes.forEach { allowHeadersPrefixed(it) }
                    config.cors.allowMethods.forEach { allowMethod(when(it) {
                        "GET"     -> HttpMethod.Get
                        "POST"    -> HttpMethod.Post
                        "PUT"     -> HttpMethod.Put
                        "DELETE"  -> HttpMethod.Delete
                        "OPTIONS" -> HttpMethod.Options
                        "HEAD"    -> HttpMethod.Head
                        else      -> throw IllegalArgumentException("Invalid CORS allow method in configuration: $it")
                    }) }
                    config.cors.exposeHeaders.forEach { exposeHeader(it) }
                    maxAgeInSeconds = config.cors.maxAgeInSeconds
                    if (config.cors.allowOrigins.contains("*")) {
                        anyHost()
                    } else {
                        config.cors.allowOrigins.forEach { allowHost(it) }
                    }
                }
                install(ContentNegotiation) { json(json) }

                configureRouting()
                installHealthCheckRoute()
                installMetricsRoute()

                startLifecycleHooks()
            }
        },
        configure = {
            connector { host = config.host; port = config.port }
            config.healthcheckPort?.let { connector { host = config.host; port = it } }
            config.metricsPort?.let { connector { host = config.host; port = it } }

            connectionIdleTimeoutSeconds = config.clientConnectionMaxIdleSeconds
        },
    ).start(wait = true)
}

private fun loadConfig(configFile: File): IptvProxyConfig {
    // Read the entire config file in memory
    var configContent = configFile.readText()

    // Add env variables to the config
    val parameters: MutableMap<String, String> = dotenv.entries().associate { it.key to it.value }.toMutableMap()

    // Replace the env variable placeholders in the config
    val substitutor = StringSubstitutor(parameters)
    configContent = substitutor.replace(configContent)

    val deserializedConfig = yaml.decodeFromString(IptvProxyConfig.serializer(), configContent)

    deserializedConfig.httpProxy?.run {
        val (_, _, _, username, password) = deserializedConfig.getActualHttpProxyConfiguration()!!
        setProxyAuthenticator(username, password)
    }

    deserializedConfig.socksProxy?.run {
        val (_, _, _, username, password) = deserializedConfig.getActualSocksProxyConfiguration()!!
        setProxyAuthenticator(username, password)
    }

    return deserializedConfig
}

private fun setProxyAuthenticator(username: String?, password: String?) {
    Authenticator.setDefault(object : Authenticator() {
        @Override
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            if (username != null && password != null) {
                return PasswordAuthentication(username, password.toCharArray())
            }

            return null
        }
    })
}

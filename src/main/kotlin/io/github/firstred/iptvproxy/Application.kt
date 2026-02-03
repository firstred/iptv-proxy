package io.github.firstred.iptvproxy

import arrow.continuations.SuspendApp
import arrow.fx.coroutines.resourceScope
import com.charleskorn.kaml.InvalidPropertyValueException
import com.charleskorn.kaml.YamlException
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import io.github.firstred.iptvproxy.di.modules.appModule
import io.github.firstred.iptvproxy.dtos.config.IptvProxyConfig
import io.github.firstred.iptvproxy.plugins.appMicrometerRegistry
import io.github.firstred.iptvproxy.plugins.configureDatabase
import io.github.firstred.iptvproxy.plugins.configureRouting
import io.github.firstred.iptvproxy.plugins.installHealthCheckRoute
import io.github.firstred.iptvproxy.plugins.installMetricsRoute
import io.github.firstred.iptvproxy.plugins.ktor.server.SentryPlugin
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
import kotlinx.coroutines.awaitCancellation
import org.apache.commons.text.StringSubstitutor
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URLEncoder
import java.util.*
import kotlin.system.exitProcess
import kotlin.text.Charsets.UTF_8

var argv = emptyArray<String>()

lateinit var config: IptvProxyConfig
lateinit var dotenv: Dotenv

private val LOG: Logger = LoggerFactory.getLogger(Application::class.java)

fun main(args: Array<String>) {
    // Executes application as KtArrow SuspendApp with graceful shutdown support
    try {
        argv = args
        dotenv = dotenv { ignoreIfMissing = true }
        val sentryDsn = dotenv.get("SENTRY_DSN") ?: ""

        TimeZone.setDefault(TimeZone.getTimeZone(dotenv.get("TZ") ?: "UTC"))

        // Initializes Sentry with configured options
        if (sentryDsn.isNotEmpty()) Sentry.init { options ->
            options.dsn = sentryDsn
            options.release = config.sentry?.release ?: "${BuildConfig.APP_PACKAGE_NAME}@${BuildConfig.APP_VERSION}"
            options.isDebug = config.sentry?.debug ?: dotenv.get("SENTRY_DEBUG").toBoolean()
        }

        object : CliktCommand(printHelpOnEmptyArgs = false) {
            private val configFile: String? by option(
                "--config",
                "-c",
                help = "Location of the configuration file",
            )

            override fun run() {
                // Loads configuration; exits on parsing errors
                try {
                    LOG.info("Loading config...")
                    config = loadConfig(File(configFile ?: "config.yml"))
                } catch (e: InvalidPropertyValueException) {
                    Sentry.captureException(e)
                    LOG.error("Invalid property `${e.propertyName}` in config file: ${e.reason}")
                    exitProcess(1)
                } catch (e: YamlException) {
                    Sentry.captureException(e)
                    LOG.error("Error parsing config file: ${e.message}")
                    exitProcess(1)
                }

                SuspendApp(timeout = config.gracefulShutdownPeriod) {
                    resourceScope {
                        startServer()
                        awaitCancellation()
                    }
                }
            }
        }
            .main(args)
    } catch (e: Exception) {
        Sentry.captureException(e)
        LOG.error("fatal error", e)
        exitProcess(1)
    }
}

private fun startServer() {
    // Starts embedded server with configured modules and connectors
    embeddedServer(
        CIO,
        serverConfig {
            module {
                install(Koin) {
                    slf4jLogger()
                    modules(appModule)
                }
                configureDatabase()

                install(SentryPlugin)
                install(AutoHeadResponse)
                install(CallLogging) {
                    logger = LOG
                    level = Level.TRACE

                    defaultCallLoggingFormat()
                }
                install(MicrometerMetrics) {
                    registry = appMicrometerRegistry
                }
                if (config.compressResponses) install(Compression) {
                    gzip { priority = .5 }
                    deflate { priority = .3 }
                }
                // Configures cross-origin resource sharing based on config
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
                    maxAgeInSeconds = config.cors.maxAgeInSeconds.toLong()
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
            connector { host = config.host; port = config.port.toInt() }
            config.healthcheckPort?.let { connector { host = config.host; port = it.toInt() } }
            config.metricsPort?.let { connector { host = config.host; port = it.toInt() } }

            connectionIdleTimeoutSeconds = config.clientConnectionMaxIdleSeconds.toInt()
        },
    ).start(wait = true)
}

fun loadConfig(configFile: File): IptvProxyConfig {
    // Read the entire config file in memory
    return loadConfig(configFile.readText())
}
/**
 * Loads configuration; validates users; configures proxy authentication
 */
fun loadConfig(configFile: String): IptvProxyConfig {
    var configContent = configFile
    // Add env variables to the config
    val parameters: MutableMap<String, String> = dotenv.entries().associate { it.key to it.value }.toMutableMap()

    // Replace the env variable placeholders in the config
    val substitutor = StringSubstitutor(parameters)
    configContent = substitutor.replace(configContent)

    val deserializedConfig = yaml.decodeFromString(IptvProxyConfig.serializer(), configContent)

    for (user in deserializedConfig.users) {
        validateUsernameOrPassword(user.username)
        validateUsernameOrPassword(user.password)
    }

    deserializedConfig.httpProxy?.run {
        val (_, _, _, username, password) = deserializedConfig.getActualHttpProxyConfiguration()!!
        if (username == null && password == null) return@run

        setProxyAuthenticator(username, password)
    }

    deserializedConfig.socksProxy?.run {
        val (_, _, _, username, password) = deserializedConfig.getActualSocksProxyConfiguration()!!
        if (username == null && password == null) return@run

        setProxyAuthenticator(username, password)

        try {
            username?.let { System.setProperty("java.net.socks.username", it) }
            password?.let { System.setProperty("java.net.socks.password", it) }
        } catch (e: SecurityException) {
            Sentry.captureException(e)
            LOG.error("Error setting SOCKS proxy username and password: ${e.message}")
        }
    }

    return deserializedConfig
}

/**
 * Validates username/password against empty or unsafe characters
 */
private fun validateUsernameOrPassword(usernameOrPassword: String) {
    if (usernameOrPassword.isEmpty()) {
        throw IllegalArgumentException("Username or password cannot be empty")
    }
    // Enforces username/password restrictions against unsafe characters
    if (usernameOrPassword.contains("_") || usernameOrPassword.contains(";") || usernameOrPassword.contains(":") || usernameOrPassword.contains("/")) {
        throw IllegalArgumentException("Password/username cannot contain _ , ; : /")
    }
    if (URLEncoder.encode(usernameOrPassword, UTF_8.toString()) != usernameOrPassword) {
        throw IllegalArgumentException("Username or password contains characters that are not allowed in a URL, which is required for IPTV: $usernameOrPassword")
    }
}

private fun setProxyAuthenticator(username: String?, password: String?) {
    // Sets global authenticator using provided credentials
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

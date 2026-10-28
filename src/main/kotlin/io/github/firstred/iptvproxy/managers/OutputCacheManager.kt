package io.github.firstred.iptvproxy.managers

import com.mayakapps.kache.FileKache
import io.github.firstred.iptvproxy.classes.CachedOutput
import io.github.firstred.iptvproxy.classes.CachedOutputSettings
import io.github.firstred.iptvproxy.classes.CachedOutputType
import io.github.firstred.iptvproxy.classes.IptvUser
import io.github.firstred.iptvproxy.classes.toCachedOutputSettings
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.repositories.ChannelRepository
import io.github.firstred.iptvproxy.db.repositories.EpgRepository
import io.github.firstred.iptvproxy.db.repositories.XtreamRepository
import io.github.firstred.iptvproxy.di.modules.IptvServersByName
import io.github.firstred.iptvproxy.events.ChannelsAreAvailableEvent
import io.github.firstred.iptvproxy.listeners.hooks.HasOnApplicationEventHook
import io.github.firstred.iptvproxy.serialization.yaml
import io.github.firstred.iptvproxy.utils.copyReplacingTo
import io.github.firstred.iptvproxy.utils.writeLiveCategories
import io.github.firstred.iptvproxy.utils.writeMovieCategories
import io.github.firstred.iptvproxy.utils.writeSeriesCategories
import io.github.firstred.iptvproxy.utils.writeXmltv
import io.github.firstred.iptvproxy.utils.writeXtreamLiveStreams
import io.github.firstred.iptvproxy.utils.writeXtreamMovies
import io.github.firstred.iptvproxy.utils.writeXtreamSeries
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource

class OutputCacheManager(
    private val playlistCache: FileKache,
    private val cacheCoroutineScope: CoroutineScope,
    private val databaseMutex: Mutex,
    private val channelManager: ChannelManager,
    private val channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository,
    private val xtreamRepository: XtreamRepository,
    private val serversByName: IptvServersByName,
) : HasOnApplicationEventHook {
    private val refreshMutex = Mutex()
    private val warmUpMutex = Mutex()
    private val pendingRefreshes = ConcurrentHashMap<String, Deferred<Boolean>>()
    private val lastRequested = ConcurrentHashMap<String, RegisteredOutput>()
    private val configFingerprint = sha256(yaml.encodeToString(config))

    @Volatile
    private var lastDataUpdateAt: Instant? = null

    /**
     * Responds with the cached output when available, the actual [baseUrl] and the credentials of the requesting user
     * are injected while streaming
     */
    suspend fun respond(call: ApplicationCall, output: CachedOutput, baseUrl: URI) {
        if (!config.cache.enabled) {
            respondLive(call, output, baseUrl)
            return
        }

        val key = output.cacheKey(configFingerprint)
        lastRequested[key] = RegisteredOutput(output, Clock.System.now())

        val cachedPath = getCachedPath(key)
        if (cachedPath != null) {
            val file = File(cachedPath)
            if (file.isFile) {
                val stale = output.isStale(file.lastModified(), lastDataUpdateAt)
                val input = try {
                    file.inputStream()
                } catch (_: IOException) {
                    null
                }

                if (input != null) {
                    call.response.header("X-Cache", if (stale) "STALE" else "HIT")
                    // Length of outputs containing placeholders changes once the actual values are injected
                    if (!output.type.containsPlaceholders) {
                        call.response.header(HttpHeaders.ContentLength, input.channel.size().toString())
                    }
                    if (stale) enqueueRefresh(output)
                    try {
                        val replacements = output.placeholderReplacements(baseUrl)
                        call.respondOutputStream(contentType = output.contentType) {
                            input.use {
                                if (replacements.isNotEmpty()) {
                                    val outputStream = buffered()
                                    it.copyReplacingTo(outputStream, replacements)
                                    outputStream.flush()
                                } else {
                                    it.copyTo(this)
                                }
                            }
                        }
                    } catch (_: IOException) {
                        // Client connection closed
                    }
                    return
                }
            }
        }

        call.response.header("X-Cache", "MISS")
        enqueueRefresh(output)
        respondLive(call, output, baseUrl)
    }

    override fun <T : Any> onApplicationEvent(event: T) {
        if (event !is ChannelsAreAvailableEvent || !config.cache.enabled) return

        cacheCoroutineScope.launch(Dispatchers.IO) {
            try {
                lastDataUpdateAt = channelRepository.findLastUpdateCompletedAt()

                warmUpMutex.withLock {
                    val now = Clock.System.now()
                    val usernamesBySettings = configuredUsernamesBySettings()

                    // Default outputs of every configured user are the most likely to be requested, users with the
                    // same settings share their outputs, which are therefore only built once per group
                    val defaultOutputs = usernamesBySettings.keys.flatMap { defaultOutputs(it) }
                    val defaultKeys = defaultOutputs.map { it.cacheKey(configFingerprint) }.toSet()

                    // Other variants are refreshed as long as they have been requested within the retention period
                    val requestedOutputs = lastRequested.entries.toList().mapNotNull { (key, registered) ->
                        if (now - registered.lastRequestedAt > config.cache.ttl.playlists) {
                            lastRequested.remove(key, registered)
                            if (key !in defaultKeys) playlistCache.remove(key)
                            null
                        } else {
                            registered.output
                        }
                    }

                    val started = TimeSource.Monotonic.markNow()
                    val outputsBySettings = (defaultOutputs + requestedOutputs)
                        .distinctBy { it.cacheKey(configFingerprint) }
                        .groupBy { it.settings }
                    outputsBySettings.forEach { (settings, outputs) ->
                        warmUp(usernamesBySettings[settings] ?: outputs.map { it.user.username }.distinct(), outputs)
                    }
                    LOG.info(
                        "Output cache warm-up completed for {} user(s) with {} distinct setting(s) in {}",
                        usernamesBySettings.values.sumOf { it.size },
                        outputsBySettings.size,
                        started.elapsedNow(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("Unable to refresh playlist output cache after channel update", e)
            }
        }
    }

    /**
     * Refreshes the missing and stale outputs shared by users with the same settings and logs once all of them have
     * been refreshed
     */
    private suspend fun warmUp(usernames: List<String>, outputs: List<CachedOutput>) {
        val started = TimeSource.Monotonic.markNow()
        val users = describeUsers(usernames)
        val refreshes = outputs.filter { needsRefresh(it) }.map { enqueueRefresh(it) }
        if (refreshes.isEmpty()) {
            LOG.info("Output cache of {} is already up to date ({} outputs)", users, outputs.size)
            return
        }

        LOG.debug("Warming up output cache of {}: refreshing {} of {} outputs", users, refreshes.size, outputs.size)
        val failed = refreshes.awaitAll().count { !it }
        if (0 == failed) {
            LOG.info(
                "Output cache of {} has been warmed up: refreshed {} of {} outputs in {}",
                users,
                refreshes.size,
                outputs.size,
                started.elapsedNow(),
            )
        } else {
            LOG.warn(
                "Output cache of {} has been partially warmed up: {} of {} outputs failed to refresh in {}",
                users,
                failed,
                refreshes.size,
                started.elapsedNow(),
            )
        }
    }

    private suspend fun needsRefresh(output: CachedOutput): Boolean {
        val cachedFile = getCachedPath(output.cacheKey(configFingerprint))?.let(::File)
        return null == cachedFile || !cachedFile.isFile || output.isStale(cachedFile.lastModified(), lastDataUpdateAt)
    }

    private suspend fun getCachedPath(key: String): String? = try {
        playlistCache.getIfAvailable(key)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LOG.warn("Unable to read cached playlist output", e)
        null
    }

    /**
     * Schedules a rebuild of the output, an already scheduled rebuild of the same output is reused.
     *
     * The output is rendered with placeholders instead of the values of the requesting user, so the file can be shared
     * by all users with the same settings.
     *
     * @return `true` once the output has been refreshed successfully, `false` when the refresh failed
     */
    private fun enqueueRefresh(output: CachedOutput): Deferred<Boolean> {
        val key = output.cacheKey(configFingerprint)

        return pendingRefreshes.computeIfAbsent(key) {
            cacheCoroutineScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    refreshMutex.withLock {
                        databaseMutex.withLock {
                            val started = TimeSource.Monotonic.markNow()
                            playlistCache.put(key) { fileName ->
                                File(fileName).outputStream().use { outputStream ->
                                    writeOutput(
                                        output,
                                        output.settings.toPlaceholderUser(),
                                        CachedOutput.PLACEHOLDER_BASE_URL,
                                        CachedOutput.PLACEHOLDER_ENCRYPTED_ACCOUNT,
                                        outputStream,
                                    )
                                    outputStream.flush()
                                }
                                true
                            }
                            LOG.debug(
                                "Refreshed cached {} output for {} in {}",
                                output.type,
                                describeUsers(output),
                                started.elapsedNow(),
                            )
                        }
                    }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LOG.warn("Unable to refresh cached ${output.type} output for ${describeUsers(output)}", e)
                    false
                } finally {
                    pendingRefreshes.remove(key)
                }
            }
        }.also { it.start() }
    }

    private suspend fun respondLive(call: ApplicationCall, output: CachedOutput, baseUrl: URI) {
        try {
            call.respondOutputStream(contentType = output.contentType) {
                writeOutput(output, output.user, baseUrl, output.user.toEncryptedAccountHexString(), this)
            }
        } catch (_: IOException) {
            // Client connection closed
        }
    }

    private fun writeOutput(
        output: CachedOutput,
        user: IptvUser,
        baseUrl: URI,
        encryptedAccount: String,
        outputStream: OutputStream,
    ) {
        when (output.type) {
            CachedOutputType.PLAYLIST -> channelManager.getAllChannelsPlaylist(
                outputStream,
                user,
                baseUrl,
                includeTypes = output.includeTypes,
                excludeTypes = output.excludeTypes,
                encryptedAccount = encryptedAccount,
            )

            else -> OutputStreamWriter(outputStream, UTF_8).apply {
                when (output.type) {
                    CachedOutputType.XMLTV -> writeXmltv(epgRepository, user, baseUrl, serversByName, encryptedAccount)
                    CachedOutputType.LIVE_STREAMS -> writeXtreamLiveStreams(
                        xtreamRepository, channelRepository, user, baseUrl, serversByName, output.categoryId, encryptedAccount
                    )
                    CachedOutputType.MOVIES -> writeXtreamMovies(
                        xtreamRepository, user, baseUrl, serversByName, output.categoryId, encryptedAccount
                    )
                    CachedOutputType.SERIES -> writeXtreamSeries(
                        xtreamRepository, user, baseUrl, serversByName, output.categoryId, encryptedAccount
                    )
                    CachedOutputType.LIVE_CATEGORIES -> writeLiveCategories(xtreamRepository)
                    CachedOutputType.MOVIE_CATEGORIES -> writeMovieCategories(xtreamRepository)
                    CachedOutputType.SERIES_CATEGORIES -> writeSeriesCategories(xtreamRepository)
                    CachedOutputType.PLAYLIST -> Unit
                }
                flush()
            }
        }
        outputStream.flush()
    }

    private fun defaultOutputs(settings: CachedOutputSettings): List<CachedOutput> = settings.toPlaceholderUser().let { user ->
        buildList {
            add(CachedOutput(CachedOutputType.PLAYLIST, user))
            add(CachedOutput(CachedOutputType.XMLTV, user))
            add(CachedOutput(CachedOutputType.LIVE_STREAMS, user))
            if (user.moviesEnabled) add(CachedOutput(CachedOutputType.MOVIES, user))
            if (user.seriesEnabled) add(CachedOutput(CachedOutputType.SERIES, user))
            add(CachedOutput(CachedOutputType.LIVE_CATEGORIES, user))
            add(CachedOutput(CachedOutputType.MOVIE_CATEGORIES, user))
            add(CachedOutput(CachedOutputType.SERIES_CATEGORIES, user))
        }
    }

    /**
     * Groups the usernames of all configured users by the settings that affect their outputs
     */
    private fun configuredUsernamesBySettings(): Map<CachedOutputSettings, List<String>> = config.users
        .map { it.toIptvUser() }
        .groupBy({ it.toCachedOutputSettings() }, { it.username })

    private fun describeUsers(output: CachedOutput): String = describeUsers(
        configuredUsernamesBySettings()[output.settings] ?: listOf(output.user.username)
    )

    private fun describeUsers(usernames: List<String>): String = if (1 == usernames.size) {
        "user ${usernames.first()}"
    } else {
        "users ${usernames.joinToString(", ")} (identical settings)"
    }

    private val CachedOutput.contentType: ContentType
        get() = when (type) {
            CachedOutputType.PLAYLIST -> ContentType.parse("audio/mpegurl")
            CachedOutputType.XMLTV -> ContentType.Application.Xml
            else -> ContentType.Application.Json
        }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class RegisteredOutput(val output: CachedOutput, val lastRequestedAt: Instant)

    companion object {
        private val LOG = LoggerFactory.getLogger(OutputCacheManager::class.java)
    }
}

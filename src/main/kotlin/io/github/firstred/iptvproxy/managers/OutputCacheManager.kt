package io.github.firstred.iptvproxy.managers

import com.mayakapps.kache.FileKache
import io.github.firstred.iptvproxy.classes.CachedOutput
import io.github.firstred.iptvproxy.classes.CachedOutputType
import io.github.firstred.iptvproxy.classes.IptvUser
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
import kotlinx.coroutines.Dispatchers
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
    private val pendingKeys = ConcurrentHashMap.newKeySet<String>()
    private val lastRequested = ConcurrentHashMap<String, RegisteredOutput>()
    private val configFingerprint = sha256(yaml.encodeToString(config))

    @Volatile
    private var lastDataUpdateAt: Instant? = null

    /**
     * Responds with the cached output when available, the actual [baseUrl] of the request is injected while streaming
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
                    // Length of outputs containing the base URL changes once the actual base URL is injected
                    if (!output.type.containsBaseUrl) {
                        call.response.header(HttpHeaders.ContentLength, input.channel.size().toString())
                    }
                    if (stale) enqueueRefresh(output)
                    try {
                        call.respondOutputStream(contentType = output.contentType) {
                            input.use {
                                if (output.type.containsBaseUrl) {
                                    val outputStream = buffered()
                                    it.copyReplacingTo(
                                        outputStream,
                                        output.encodeBaseUrl(CachedOutput.PLACEHOLDER_BASE_URL),
                                        output.encodeBaseUrl(baseUrl),
                                    )
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
                val now = Clock.System.now()

                // Warm the default outputs of every configured user first, they are the most likely to be requested
                val defaultOutputs = defaultOutputs(config.users.map { it.toIptvUser() })
                defaultOutputs.forEach { enqueueIfStale(it) }
                val defaultKeys = defaultOutputs.map { it.cacheKey(configFingerprint) }.toSet()

                lastRequested.entries.toList().forEach { (key, registered) ->
                    if (now - registered.lastRequestedAt > config.cache.ttl.playlists) {
                        lastRequested.remove(key, registered)
                        if (key !in defaultKeys) playlistCache.remove(key)
                    } else {
                        enqueueIfStale(registered.output)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("Unable to refresh playlist output cache after channel update", e)
            }
        }
    }

    private suspend fun enqueueIfStale(output: CachedOutput) {
        val key = output.cacheKey(configFingerprint)
        val cachedPath = getCachedPath(key)
        val cachedFile = cachedPath?.let(::File)
        if (null == cachedFile || !cachedFile.isFile || output.isStale(cachedFile.lastModified(), lastDataUpdateAt)) {
            enqueueRefresh(output)
        }
    }

    private suspend fun getCachedPath(key: String): String? = try {
        playlistCache.getIfAvailable(key)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LOG.warn("Unable to read cached playlist output", e)
        null
    }

    private fun enqueueRefresh(output: CachedOutput) {
        val key = output.cacheKey(configFingerprint)
        if (!pendingKeys.add(key)) return

        cacheCoroutineScope.launch(Dispatchers.IO) {
            try {
                refreshMutex.withLock {
                    databaseMutex.withLock {
                        playlistCache.put(key) { fileName ->
                            File(fileName).outputStream().use { outputStream ->
                                writeOutput(output, CachedOutput.PLACEHOLDER_BASE_URL, outputStream)
                                outputStream.flush()
                            }
                            true
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("Unable to refresh cached ${output.type} output for user ${output.user.username}", e)
            } finally {
                pendingKeys.remove(key)
            }
        }
    }

    private suspend fun respondLive(call: ApplicationCall, output: CachedOutput, baseUrl: URI) {
        try {
            call.respondOutputStream(contentType = output.contentType) {
                writeOutput(output, baseUrl, this)
            }
        } catch (_: IOException) {
            // Client connection closed
        }
    }

    private fun writeOutput(output: CachedOutput, baseUrl: URI, outputStream: OutputStream) {
        when (output.type) {
            CachedOutputType.PLAYLIST -> channelManager.getAllChannelsPlaylist(
                outputStream,
                output.user,
                baseUrl,
                includeTypes = output.includeTypes,
                excludeTypes = output.excludeTypes,
            )

            else -> OutputStreamWriter(outputStream, UTF_8).apply {
                when (output.type) {
                    CachedOutputType.XMLTV -> writeXmltv(epgRepository, output.user, baseUrl, serversByName)
                    CachedOutputType.LIVE_STREAMS -> writeXtreamLiveStreams(
                        xtreamRepository, channelRepository, output.user, baseUrl, serversByName, output.categoryId
                    )
                    CachedOutputType.MOVIES -> writeXtreamMovies(
                        xtreamRepository, output.user, baseUrl, serversByName, output.categoryId
                    )
                    CachedOutputType.SERIES -> writeXtreamSeries(
                        xtreamRepository, output.user, baseUrl, serversByName, output.categoryId
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

    private fun defaultOutputs(users: List<IptvUser>): List<CachedOutput> = users.flatMap { user ->
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

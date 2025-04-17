package io.github.firstred.iptvproxy

import io.github.firstred.iptvproxy.config.IptvConnectionConfig
import io.github.firstred.iptvproxy.config.IptvProxyConfig
import io.github.firstred.iptvproxy.config.IptvServerConfig
import io.github.firstred.iptvproxy.m3u.M3uChannel
import io.github.firstred.iptvproxy.m3u.M3uParser
import io.github.firstred.iptvproxy.utils.digest.Digest
import io.github.firstred.iptvproxy.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.xmltv.XmltvDoc
import io.github.firstred.iptvproxy.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.xmltv.XmltvText
import io.github.firstred.iptvproxy.xmltv.XmltvUtils
import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URISyntaxException
import java.net.http.HttpClient
import java.nio.ByteBuffer
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import java.util.regex.Pattern
import kotlin.concurrent.Volatile

class IptvProxyService(config: IptvProxyConfig) : HttpHandler {
    private class IptvServerGroup(
        val name: String?,
        val xmltvUrl: String?,
        val xmltvBefore: Duration?,
        val xmltvAfter: Duration?,
        val groupFilters: List<Pattern?>?
    ) {
        val servers: MutableList<IptvServer> = ArrayList()

        var xmltvCache: ByteArray = byteArrayOf()
    }

    private val undertow: Undertow = Undertow.builder()
        .addHttpListener(config.port, config.host)
        .setHandler(this)
        .build()

    private val scheduler = createScheduler()

    private val baseUrl = BaseUrl(config.baseUrl.toString(), config.forwardedPass)

    private val tokenSalt: String? = config.tokenSalt

    private val idCounter = AtomicLong(System.currentTimeMillis())

    private val serverGroups: MutableList<IptvServerGroup> = ArrayList()

    @Volatile
    private var channels: Map<String, IptvChannel> = HashMap()
    private var serverChannelsByUrl: Map<String?, IptvServerChannel> = HashMap()

    private val users: MutableMap<String, IptvUser> = ConcurrentHashMap()

    private val allowAnonymous = config.allowAnonymous
    private val allowedUsers: Set<String?> = config.users

    private val channelsLoader: AsyncLoader<String> = AsyncLoader.stringLoader(
        config.channelsTimeoutSec,
        config.channelsTotalTimeoutSec,
        config.channelsRetryDelayMs,
        scheduler
    )
    private val xmltvLoader: AsyncLoader<ByteArray> = AsyncLoader.bytesLoader(
        config.xmltvTimeoutSec,
        config.xmltvTotalTimeoutSec,
        config.xmltvRetryDelayMs,
        scheduler
    )

    @Volatile
    private var xmltvData: ByteArray? = null

    private val defaultHttpClient: HttpClient = HttpClient.newBuilder()
        .version(if (config.useHttp2) HttpClient.Version.HTTP_2 else HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()

    init {
        config.servers?.forEach(Consumer { sc: IptvServerConfig ->
            val sg = IptvServerGroup(sc.name, sc.xmltvUrl, sc.xmltvBefore, sc.xmltvAfter, sc.groupFilters)
            serverGroups.add(sg)
            sc.connections?.forEach(Consumer { cc: IptvConnectionConfig ->
                sg.servers.add(
                    IptvServer(
                        sc,
                        cc,
                        defaultHttpClient
                    )
                )
            })
        })
    }

    fun startService() {
        LOG.info("starting")

        updateChannels()

        undertow.start()

        LOG.info("started")
    }

    fun stopService() {
        LOG.info("stopping")

        try {
            scheduler.shutdownNow()
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                LOG.warn("scheduler is still running...")
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            LOG.error("interrupted while stopping scheduler")
        }

        undertow.stop()

        LOG.info("stopped")
    }

    private fun scheduleChannelsUpdate(delayMins: Long) {
        scheduler.schedule({ Thread { this.updateChannels() }.start() }, delayMins, TimeUnit.MINUTES)
    }

    private fun updateChannels() {
        if (updateChannelsImpl()) {
            scheduleChannelsUpdate(240)
        } else {
            scheduleChannelsUpdate(1)
        }
    }

    private fun updateChannelsImpl(): Boolean {
        LOG.info("updating channels")

        val chs: MutableMap<String, IptvChannel> = HashMap()
        val byUrl: MutableMap<String?, IptvServerChannel> = HashMap()

        val digest: Digest = Digest.sha256()
        val md5: Digest = Digest.md5()

        val loads: MutableMap<IptvServer, CompletableFuture<String?>> = HashMap()
        val xmltvLoads: MutableMap<IptvServerGroup, CompletableFuture<ByteArray?>> = HashMap()
        serverGroups.forEach(Consumer { sg: IptvServerGroup ->
            if (sg.xmltvUrl != null) xmltvLoads[sg] = loadXmltv(sg)
            sg.servers.forEach(Consumer { s: IptvServer -> loads[s] = loadChannels(s) })
        })

        val newXmltv = XmltvDoc()
            .setChannels(ArrayList())
            .setProgrammes(ArrayList())
            .setGeneratorName("iptvproxy")

        for (sg in serverGroups) {
            var xmltv: XmltvDoc? = null
            if (sg.xmltvUrl != null) {
                LOG.info("waiting for xmltv data to be downloaded")

                var data: ByteArray? = null

                try {
                    data = xmltvLoads[sg]!!.get()
                } catch (e: InterruptedException) {
                    LOG.warn("error loading xmltv data")
                } catch (e: ExecutionException) {
                    LOG.warn("error loading xmltv data")
                }

                if (data != null) {
                    LOG.info("parsing xmltv data")

                    xmltv = XmltvUtils.parseXmltv(data)
                    if (xmltv != null) {
                        sg.xmltvCache = data
                    }
                }

                if (xmltv == null) xmltv = XmltvUtils.parseXmltv(sg.xmltvCache)
            }

            val xmltvById: MutableMap<String?, XmltvChannel?> = HashMap()
            val xmltvByName: MutableMap<String?, XmltvChannel?> = HashMap()

            xmltv?.channels?.forEach(Consumer outerForEach@{ ch: XmltvChannel? ->
                if (ch == null) return@outerForEach

                xmltvById[ch.id] = ch
                ch.displayNames?.forEach(Consumer innerForEach@{ n: XmltvText? ->
                    if (n == null) return@innerForEach

                    xmltvByName[n.text] = ch
                })
            })

            val xmltvIds: MutableMap<String?, String?> = HashMap()

            for (server in sg.servers) {
                LOG.info("parsing playlist: {}, url: {}", sg.name, server.url)

                var channels: String? = null

                try {
                    channels = loads[server]!!.get()
                } catch (e: InterruptedException) {
                    LOG.error("error waiting for channels load", e)
                } catch (e: ExecutionException) {
                    LOG.error("error waiting for channels load", e)
                }

                if (channels == null) return false

                val m3u = M3uParser.parse(channels)
                if (m3u == null) {
                    LOG.error("error parsing m3u, update skipped")
                    return false
                }

                m3u.channels.forEach { c: M3uChannel? ->
                    if (c == null) return@forEach

                    // Unique ID will be formed from server name and channel name.
                    // It seems that there will be no any other suitable way to identify channel.
                    val id = digest.digest(sg.name + "||" + c.name)
                    val url = c.url

                    var channel = chs[id]
                    if (channel == null) {
                        val tvgId = c.getProp("tvg-id")
                        val tvgName = c.getProp("tvg-name")

                        if (sg.groupFilters!!.isNotEmpty()) {
                            if (c.groups.stream().noneMatch { g: String? ->
                                    sg.groupFilters.stream().anyMatch { f: Pattern? -> f!!.matcher(g).find() }
                                }) {
                                // skip channel - filtered by group filter
                                return@forEach
                            }
                        }

                        var xmltvCh: XmltvChannel? = null
                        if (tvgId != null) {
                            xmltvCh = xmltvById[tvgId]
                        }
                        if (xmltvCh == null && tvgName != null) {
                            xmltvCh = xmltvByName[tvgName]
                            if (xmltvCh == null) {
                                xmltvCh = xmltvByName[tvgName.replace(' ', '_')]
                            }
                        }
                        if (xmltvCh == null) {
                            xmltvCh = xmltvByName[c.name]
                        }

                        var logo = c.getProp("tvg-logo")

                        if (logo == null && xmltvCh != null && xmltvCh.icon != null && xmltvCh.icon!!.src != null) {
                            xmltvCh.icon?.src?.let { logo = it }
                        }

                        var days = 0
                        var daysStr = c.getProp("tvg-rec")
                        if (daysStr == null) {
                            daysStr = c.getProp("catchup-days")
                        }
                        if (daysStr != null) {
                            try {
                                days = daysStr.toInt()
                            } catch (e: NumberFormatException) {
                                LOG.warn("error parsing catchup days: {}, channel: {}", daysStr, c.name)
                            }
                        }

                        var xmltvId = xmltvCh?.id
                        if (xmltvId != null) {
                            val newId = md5.digest(sg.name + '-' + xmltvId)
                            if (xmltvIds.putIfAbsent(xmltvId, newId) == null) {
                                newXmltv.channels?.add(XmltvChannel().setId(newId))
                            }
                            xmltvId = newId
                        }

                        channel = IptvChannel(id, c.name, logo.toString(), c.groups, xmltvId.toString(), days)
                        chs[id] = channel
                    }

                    var serverChannel = serverChannelsByUrl[url]
                    if (serverChannel == null) {
                        try {
                            serverChannel = IptvServerChannel(
                                server,
                                url,
                                baseUrl.forPath("/$id"),
                                id,
                                c.name,
                                scheduler,
                            )
                        } catch (e: URISyntaxException) {
                            throw RuntimeException("error creating server channel", e)
                        }
                    }

                    channel.addServerChannel(serverChannel)
                    LOG.info("Add server channel for channel: {}, url: {}", c.name, url)

                    chs[id] = channel
                    byUrl[url] = serverChannel
                }
            }

            val endOf = if (sg.xmltvAfter == null) null else ZonedDateTime.now().plus(sg.xmltvAfter)
            val startOf = if (sg.xmltvBefore == null) null else ZonedDateTime.now().minus(sg.xmltvBefore)

            xmltv?.programmes?.forEach(Consumer { p: XmltvProgramme? ->
                if ((endOf == null || p!!.start!! < endOf) && (startOf == null || p!!.stop!! > startOf)
                ) {
                    val newId = xmltvIds[p!!.channel]
                    if (newId != null) {
                        newXmltv.programmes?.add(p.copy().setChannel(newId))
                    }
                }
            })
        }

        xmltvData = XmltvUtils.writeXmltv(newXmltv)
        channels = chs
        serverChannelsByUrl = byUrl

        LOG.info("{} channels updated", channels.size)

        return true
    }

    private fun loadXmltv(sg: IptvServerGroup): CompletableFuture<ByteArray?> {
        val f = FileLoader.tryLoadBytes(sg.xmltvUrl!!)
        return f ?: xmltvLoader.loadAsync("xmltv: " + sg.name, sg.xmltvUrl, defaultHttpClient)
    }

    private fun loadChannels(s: IptvServer): CompletableFuture<String?> {
        val f = FileLoader.tryLoadString(s.url.toString())
        return f ?: channelsLoader.loadAsync("playlist: " + s.name, s.createRequest(s.url.toString()).build(), s.httpClient)
    }

    override fun handleRequest(exchange: HttpServerExchange) {
        if (!handleInternal(exchange)) {
            exchange.setStatusCode(HttpURLConnection.HTTP_NOT_FOUND)
            exchange.responseSender.send("N/A")
        }
    }

    private fun handleInternal(exchange: HttpServerExchange): Boolean {
        var path = exchange.requestPath

        if (path.startsWith("/")) {
            path = path.substring(1)
        }

        if (path.startsWith("m3u")) {
            return handleM3u(exchange, path)
        }

        if (path.startsWith("epg.xml.gz")) {
            return handleEpg(exchange)
        }

        // channels
        val idx = path.indexOf('/')
        if (idx < 0) {
            LOG.warn("wrong request: {}", exchange.requestPath)
            return false
        }

        val ch = path.substring(0, idx)
        path = path.substring(idx + 1)

        val channel = channels[ch]
        if (channel == null) {
            LOG.warn("channel not found: {}, for request: {}", ch, exchange.requestPath)
            return false
        }

        // we need user if this is not m3u request
        val token = exchange.queryParameters.getOrDefault(TOKEN_TAG, ArrayDeque()).peek()
        var user = getUserFromToken(token)

        // pass user name from another iptv-proxy
        val proxyUser: String? = exchange.requestHeaders.getFirst(IptvServer.PROXY_USER_HEADER)

        // no token, or user is not verified
        if (user == null) {
            LOG.warn("invalid user token: {}, proxyUser: {}", token, proxyUser)
            return false
        }

        if (proxyUser != null) {
            user = "$user:$proxyUser"
        }

        val iu = users.computeIfAbsent(user) { u: String ->
            IptvUser(
                u, scheduler
            ) { key: String, value: IptvUser -> users.remove(key, value) }
        }
        iu.lock()
        try {
            val serverChannel = iu.getServerChannel(channel) ?: return false

            return serverChannel.handle(exchange, path, iu, token)
        } finally {
            iu.unlock()
        }
    }

    private fun getUserFromToken(token: String?): String? {
        if (token == null) {
            return null
        }

        val idx = token.lastIndexOf('-')
        if (idx < 0) {
            return null
        }

        val digest = token.substring(idx + 1)
        val user = token.substring(0, idx)

        if (digest == Digest.md5(user + tokenSalt)) {
            return user
        }

        return null
    }

    private fun generateUser(): String {
        return idCounter.incrementAndGet().toString()
    }

    private fun generateToken(user: String): String {
        return user + '-' + Digest.md5(user + tokenSalt)
    }

    private fun handleM3u(exchange: HttpServerExchange, path: String): Boolean {
        var user: String? = null

        val idx = path.indexOf('/')
        if (idx >= 0) {
            user = path.substring(idx + 1)
            if (!allowedUsers.contains(user)) user = null
        }

        if (user == null && allowAnonymous) user = generateUser()

        if (user == null) {
            LOG.warn("user not defined for request: {}", exchange.requestPath)
            return false
        }

        val token = generateToken(user)

        exchange.responseHeaders
            .add(Headers.CONTENT_TYPE, "audio/mpegurl")
            .add(Headers.CONTENT_DISPOSITION, "attachment; filename=playlist.m3u")
            .add(HttpUtils.ACCESS_CONTROL, "*")

        val chs: List<IptvChannel> = ArrayList(channels.values).let {
            if (config.sortChannels) it.sortedBy { obj: IptvChannel -> obj.name }
            else it
        }

        val sb = StringBuilder()
        sb.append("#EXTM3U\n")

        chs.forEach(Consumer { ch: IptvChannel ->
            sb.append("#EXTINF:0")
            if (ch.xmltvId.isNotEmpty()) {
                sb.append(" tvg-id=\"").append(ch.xmltvId).append('"')
            }

            if (ch.logo.isNotEmpty()) {
                sb.append(" tvg-logo=\"").append(ch.logo).append('"')
            }

            if (ch.catchupDays > 0) {
                sb.append(" catchup=\"shift\" catchup-days=\"").append(ch.catchupDays).append('"')
            }

            sb.append(',').append(ch.name).append("\n")

            if (ch.groups.isNotEmpty()) {
                sb.append("#EXTGRP:").append(java.lang.String.join(";", ch.groups)).append("\n")
            }
            sb.append(baseUrl.getBaseUrl(exchange))
                .append('/')
                .append(ch.id)
                .append("/channel.m3u8?")
                .append(TOKEN_TAG)
                .append("=")
                .append(token)
                .append("\n")
        })

        exchange.responseSender.send(sb.toString())

        return true
    }

    private fun handleEpg(exchange: HttpServerExchange): Boolean {
        val epg = xmltvData ?: return false

        exchange.responseHeaders
            .add(Headers.CONTENT_TYPE, "application/octet-stream")
            .add(Headers.CONTENT_DISPOSITION, "attachment; filename=epg.xml.gz")
            .add(Headers.CONTENT_LENGTH, epg.size.toString())

        exchange.responseSender.send(ByteBuffer.wrap(epg))

        return true
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IptvProxyService::class.java)

        private const val TOKEN_TAG = "t"

        private const val SCHEDULER_THREADS = 2
        private fun createScheduler(): ScheduledExecutorService {
            val s = ScheduledThreadPoolExecutor(
                SCHEDULER_THREADS
            ) { r: Runnable?, e: ThreadPoolExecutor? -> LOG.error("execution rejected") }
            s.removeOnCancelPolicy = true
            s.maximumPoolSize = SCHEDULER_THREADS

            return s
        }
    }
}

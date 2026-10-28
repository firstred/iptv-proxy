package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.classes.IptvUser
import io.github.firstred.iptvproxy.db.repositories.ChannelRepository
import io.github.firstred.iptvproxy.db.repositories.EpgRepository
import io.github.firstred.iptvproxy.db.repositories.XtreamRepository
import io.github.firstred.iptvproxy.di.modules.IptvServersByName
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.dtos.xtream.XtreamCategory
import io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStream
import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovie
import io.github.firstred.iptvproxy.dtos.xtream.XtreamSeries
import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.serialization.json
import io.github.firstred.iptvproxy.serialization.xml
import java.io.Writer
import java.net.URI

fun Writer.writeXmltv(
    epgRepository: EpgRepository,
    user: IptvUser,
    baseUrl: URI,
    serversByName: IptvServersByName,
    encryptedAccount: String = user.toEncryptedAccountHexString(),
) {
    write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv generator-info-name=\"iptv-proxy\">")
    epgRepository.forEachEpgChannelChunk(forUser = user) { channels ->
        channels.forEach { row ->
            write(
                xml.encodeToString(
                    XmltvChannel.serializer(), row.copy(
                        icon = row.icon?.copy(
                            src = row.icon.src?.let {
                                if (serversByName.values.any { server -> server.config.proxyStream }) {
                                    it.toProxiedIconUrl(baseUrl, encryptedAccount)
                                } else it
                            },
                        ),
                    )
                )
            )
            flush()
        }
    }
    epgRepository.forEachEpgProgrammeChunk(forUser = user) { programmes ->
        programmes.forEach { row ->
            write(xml.encodeToString(XmltvProgramme.serializer(), row))
            flush()
        }
    }

    write("</tv>")
    flush()
}

fun Writer.writeXtreamLiveStreams(
    xtreamRepository: XtreamRepository,
    channelRepository: ChannelRepository,
    user: IptvUser,
    baseUrl: URI,
    serversByName: IptvServersByName,
    categoryId: UInt? = null,
    encryptedAccount: String = user.toEncryptedAccountHexString(),
) {
    write("[")
    var first = true

    xtreamRepository.forEachLiveStreamChunk(categoryId = categoryId, forUser = user) { list ->
        list.forEach { stream ->
            if (!first) write(",") else first = false
            write(
                json.encodeToString(
                    XtreamLiveStream.serializer(), stream.copy(
                        streamIcon = if (serversByName[stream.server]?.config?.proxyStream ?: false) {
                            stream.streamIcon?.toProxiedIconUrl(baseUrl, encryptedAccount)
                        } else stream.streamIcon,
                        server = null,
                        url = if (serversByName[stream.server]?.config?.proxyStream ?: false) {
                            "$baseUrl${stream.streamType.urlType()}/${user.username}/${user.password}/${stream.streamId}.${stream.url.toString().substringAfterLast('.').ifBlank { "m3u8" }}"
                        } else stream.url,
                    )
                )
            )
            flush()
        }
    }

    channelRepository.forEachMissingIptvChannelAsLiveStreamChunk { list ->
        list.forEach { stream ->
            if (!first) write(",") else first = false
            write(
                json.encodeToString(
                    XtreamLiveStream.serializer(), stream.copy(
                        streamIcon = if (serversByName[stream.server]?.config?.proxyStream ?: false) {
                            stream.streamIcon?.toProxiedIconUrl(baseUrl, encryptedAccount)
                        } else stream.streamIcon,
                        server = null,
                        url = if (serversByName[stream.server]?.config?.proxyStream ?: false) {
                            "$baseUrl${stream.streamType.urlType()}/${user.username}/${user.password}/${stream.streamId}.m3u8"
                        } else stream.url,
                    )
                )
            )
            flush()
        }
    }

    write("]")
}

fun Writer.writeXtreamMovies(
    xtreamRepository: XtreamRepository,
    user: IptvUser,
    baseUrl: URI,
    serversByName: IptvServersByName,
    categoryId: UInt? = null,
    encryptedAccount: String = user.toEncryptedAccountHexString(),
) {
    write("[")
    var first = true

    // Movie output intentionally keeps its existing behavior and is not filtered by the user.
    xtreamRepository.forEachMovieChunk(categoryId = categoryId) { list ->
        list.forEach { movie ->
            if (!first) write(",") else first = false
            write(
                json.encodeToString(
                    XtreamMovie.serializer(), movie.copy(
                        streamIcon = if (serversByName[movie.server]?.config?.proxyStream ?: false) {
                            movie.streamIcon.toProxiedIconUrl(baseUrl, encryptedAccount)
                        } else movie.streamIcon,
                        server = null,
                        url = if (serversByName[movie.server]?.config?.proxyStream ?: false) {
                            "$baseUrl${movie.streamType.urlType()}/${user.username}/${user.password}/${movie.streamId}.${movie.url.toString().substringAfterLast('.').ifBlank { "m3u8" }}"
                        } else movie.url,
                    )
                )
            )
            flush()
        }
    }

    write("]")
}

fun Writer.writeXtreamSeries(
    xtreamRepository: XtreamRepository,
    user: IptvUser,
    baseUrl: URI,
    serversByName: IptvServersByName,
    categoryId: UInt? = null,
    encryptedAccount: String = user.toEncryptedAccountHexString(),
) {
    write("[")
    var first = true

    xtreamRepository.forEachSeriesChunk(categoryId = categoryId, forUser = user) { list ->
        list.forEach { series ->
            if (!first) write(",") else first = false
            write(
                json.encodeToString(
                    XtreamSeries.serializer(), series.copy(
                        cover = if (serversByName[series.server]?.config?.proxyStream ?: false) {
                            series.cover.toProxiedIconUrl(baseUrl, encryptedAccount)
                        } else series.cover,
                        backdropPath = if (serversByName[series.server]?.config?.proxyStream ?: false) {
                            series.backdropPath.map { it?.toProxiedIconUrl(baseUrl, encryptedAccount) }
                        } else series.backdropPath,
                        server = null,
                    )
                )
            )
            flush()
        }
    }

    write("]")
}

fun Writer.writeLiveCategories(xtreamRepository: XtreamRepository) = writeXtreamCategories(xtreamRepository, IptvChannelType.live)

fun Writer.writeMovieCategories(xtreamRepository: XtreamRepository) = writeXtreamCategories(xtreamRepository, IptvChannelType.movie)

fun Writer.writeSeriesCategories(xtreamRepository: XtreamRepository) = writeXtreamCategories(xtreamRepository, IptvChannelType.series)

private fun Writer.writeXtreamCategories(xtreamRepository: XtreamRepository, type: IptvChannelType) {
    write("[")
    var first = true
    xtreamRepository.forEachCategoryChunk(type = type) { list ->
        list.forEach { category ->
            if (!first) write(",") else first = false
            write(json.encodeToString(XtreamCategory.serializer(), category))
            flush()
        }
    }
    write("]")
}

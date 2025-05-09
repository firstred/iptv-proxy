package io.github.firstred.iptvproxy.db.repositories

import io.github.firstred.iptvproxy.classes.IptvChannel
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.tables.CategoryTable
import io.github.firstred.iptvproxy.db.tables.ChannelTable
import io.github.firstred.iptvproxy.db.tables.sources.PlaylistSourceTable
import io.github.firstred.iptvproxy.di.modules.IptvServersByName
import io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStream
import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.utils.ListFilters
import io.github.firstred.iptvproxy.utils.isFilterActive
import io.github.firstred.iptvproxy.utils.isNotGlobOrRegexp
import io.github.firstred.iptvproxy.utils.isRegexp
import io.github.firstred.iptvproxy.utils.isWhiteListOnly
import io.github.firstred.iptvproxy.utils.toChannelTypeOrNull
import io.github.firstred.iptvproxy.utils.toEncodedJavaURI
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.compoundOr
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.mp.KoinPlatform.getKoin
import java.net.URI

class ChannelRepository : KoinComponent {
    private val serversByName: IptvServersByName by inject()

    fun signalPlaylistImportStartedForServer(server: String) {
        transaction {
            PlaylistSourceTable.upsert {
                it[PlaylistSourceTable.server] = server
                it[PlaylistSourceTable.startedImportAt] = Clock.System.now()
            }
        }
    }

    fun signalPlaylistImportCompletedForServer(server: String) {
        transaction {
            PlaylistSourceTable.upsert {
                it[PlaylistSourceTable.server] = server
                it[PlaylistSourceTable.completedImportAt] = Clock.System.now()
            }
        }
    }

    fun upsertChannels(channels: List<IptvChannel>) {
        channels.chunked(config.database.chunkSize.toInt()).forEach { chunk ->
            transaction {
                ChannelTable.batchUpsert(
                    data = chunk,
                    keys = arrayOf(ChannelTable.server, ChannelTable.xtreamStreamId, ChannelTable.url),
                    shouldReturnGeneratedValues = false,
                ) { channel ->
                    this[ChannelTable.server] = channel.server.name
                    this[ChannelTable.name] = channel.name
                    this[ChannelTable.url] = channel.url.toString()
                    this[ChannelTable.mainGroup] = channel.groups.firstOrNull()
                    this[ChannelTable.groups] = channel.groups.toTypedArray()
                    this[ChannelTable.type] = channel.type
                    this[ChannelTable.epgChannelId] = channel.epgId ?: ""
                    this[ChannelTable.xtreamStreamId] = channel.url.extractStreamId().toUIntOrNull() ?: 0u
                    this[ChannelTable.icon] = channel.logo
                    this[ChannelTable.catchupDays] = channel.catchupDays.toUInt()
                    this[ChannelTable.m3uProps] = channel.m3uProps
                    this[ChannelTable.vlcOpts] = channel.vlcOpts
                    this[ChannelTable.externalPosition] = channel.externalPosition!!
                    this[ChannelTable.updatedAt] = Clock.System.now()
                }
            }
        }
    }

    fun getChannelById(id: UInt): IptvChannel? {
        return transaction {
            ChannelTable.selectAll()
                .where { ChannelTable.id eq id }
                .map {
                    IptvChannel(
                        id = it[ChannelTable.id].value,
                        externalPosition = it[ChannelTable.externalPosition],
                        externalStreamId = it[ChannelTable.xtreamStreamId],
                        server = serversByName[it[ChannelTable.server]]!!,
                        name = it[ChannelTable.name],
                        url = Url(it[ChannelTable.url]).toEncodedJavaURI(),
                        epgId = it[ChannelTable.epgChannelId],
                        logo = it[ChannelTable.icon],
                        groups = it[ChannelTable.groups].toList(),
                        catchupDays = it[ChannelTable.catchupDays]?.toInt() ?: 0,
                        m3uProps = it[ChannelTable.m3uProps],
                        vlcOpts = it[ChannelTable.vlcOpts],
                        type = it[ChannelTable.type],
                    )
                }.firstOrNull()
        }
    }

    fun forEachIptvChannelChunk(
        server: String? = null,
        listFilters: ListFilters? = null,
        sortedByName: Boolean = config.sortChannelsByName,
        chunkSize: Int = config.database.chunkSize.toInt(),
        action: (List<IptvChannel>) -> Unit,
    ) {
        var offset = 0L
        lateinit var channels: List<IptvChannel>

        do {
            transaction {
                val channelQuery = ChannelTable.selectAll()
                server?.let { channelQuery.andWhere { ChannelTable.server eq it } }
                if (sortedByName) {
                    channelQuery.orderBy(ChannelTable.name to SortOrder.ASC)
                } else {
                    channelQuery.orderBy(
                        ChannelTable.server to SortOrder.ASC,
                        ChannelTable.externalPosition to SortOrder.ASC
                    )
                }
                listFilters?.let { filters ->
                    if (filters.isNotEmpty()) {
                        if (filters.isWhiteListOnly()) {
                            channelQuery.andWhere {
                                (listOf(
                                    ChannelTable.name inList filters.channelWhitelist.filter(String::isNotGlobOrRegexp),
                                    ChannelTable.mainGroup inList filters.categoryWhitelist.filter(String::isNotGlobOrRegexp),
                                )
                                        + filters.channelWhitelist.filter(String::isRegexp).map {
                                    ChannelTable.name regexp it.substringAfter(":")
                                }
                                        + filters.categoryWhitelist.filter(String::isRegexp).map {
                                    ChannelTable.mainGroup regexp it.substringAfter(":")
                                }
                                        ).compoundOr()
                            }
                        } else if (filters.isNotEmpty() && filters.isFilterActive()) {
                            if (filters.categoryBlacklist.isNotEmpty()) {
                                channelQuery.andWhere {
                                    ChannelTable.name notInList filters.channelBlacklist.filter(String::isNotGlobOrRegexp)
                                }
                                filters.categoryBlacklist.filter(String::isRegexp).forEach {
                                    channelQuery.andWhere {
                                        ChannelTable.mainGroup regexp it.substringAfter(":")
                                    }
                                }
                            }
                            if (filters.channelBlacklist.isNotEmpty()) {
                                channelQuery.andWhere {
                                    ChannelTable.mainGroup notInList filters.categoryBlacklist.filter(String::isNotGlobOrRegexp)
                                }
                                filters.channelBlacklist.filter(String::isRegexp).forEach {
                                    channelQuery.andWhere {
                                        ChannelTable.name regexp it.substringAfter(":")
                                    }
                                }
                            }
                        }
                    }
                }
                channelQuery
                    .limit(chunkSize)
                    .offset(offset)
                channels = channelQuery.map { it.toIptvChannel() }

                if (channels.isEmpty()) return@transaction

                action(channels)
                offset += chunkSize
            }
        } while (channels.isNotEmpty())
    }

    fun forEachMissingIptvChannelAsLiveStreamChunk(
        server: String? = null,
        sortedByName: Boolean = config.sortChannelsByName,
        chunkSize: Int = config.database.chunkSize.toInt(),
        action: (List<XtreamLiveStream>) -> Unit,
    ) {
        var offset = 0L
        lateinit var channels: List<XtreamLiveStream>

        do {
            transaction {
                val channelQuery = ChannelTable
                    .join(
                        CategoryTable,
                        joinType = JoinType.INNER,
                        onColumn = ChannelTable.mainGroup,
                        otherColumn = CategoryTable.name,
                    )
                    .selectAll()
                server?.let { channelQuery.andWhere { ChannelTable.server eq it } }
                channelQuery.andWhere { ChannelTable.type eq IptvChannelType.live }
                channelQuery.andWhere { ChannelTable.xtreamStreamId eq 0u }
                if (sortedByName) {
                    channelQuery.orderBy(ChannelTable.name to SortOrder.ASC)
                } else {
                    channelQuery.orderBy(
                        ChannelTable.server to SortOrder.ASC,
                        ChannelTable.externalPosition to SortOrder.ASC
                    )
                }
                channelQuery
                    .limit(chunkSize)
                    .offset(offset)
                channels = channelQuery.map { it.toXtreamLiveStream() }

                if (channels.isEmpty()) return@transaction

                action(channels)
                offset += chunkSize
            }
        } while (channels.isNotEmpty())
    }

    fun findInternalIdsByExternalIds(externalIds: List<UInt>, server: String) = transaction {
        ChannelTable
            .select(ChannelTable.xtreamStreamId, ChannelTable.id)
            .where { ChannelTable.xtreamStreamId inList externalIds.map { it } }
            .andWhere { ChannelTable.server eq server }
            .associateBy({ it[ChannelTable.xtreamStreamId] }, { it[ChannelTable.id].value })
    }

    fun findChannelsWithMissingGroups(): Map<UInt, Pair<String, String>> = transaction {
        ChannelTable
            .join(
                CategoryTable,
                joinType = JoinType.LEFT,
                onColumn = ChannelTable.mainGroup,
                otherColumn = CategoryTable.name,
            )
            .select(ChannelTable.id, ChannelTable.server, ChannelTable.mainGroup)
            .where { CategoryTable.name.isNull() }
            .associateBy(
                { it[ChannelTable.id].value },
                { Pair(it[ChannelTable.server], it[ChannelTable.mainGroup]!!) }
            )
    }

    fun getIptvChannelCount(): UInt = transaction {
        ChannelTable.selectAll().count().toUInt()
    }

    fun cleanup() {
        val now = Clock.System.now()

        transaction {
            PlaylistSourceTable.deleteWhere {
                PlaylistSourceTable.server notInList config.servers.map { it.name }
            }

            ChannelTable.deleteWhere {
                ChannelTable.updatedAt less ((now - config.channelMaxStalePeriod).coerceAtLeast(Instant.DISTANT_PAST))
            }

            // In case of duplicate xtream stream IDs, remove the oldest ones
            do {
                val deleted = ChannelTable.deleteWhere {
                    ChannelTable.id inList ChannelTable
                        .select(
                            ChannelTable.id,
                            ChannelTable.server,
                            ChannelTable.xtreamStreamId,
                            ChannelTable.updatedAt.min(),
                        )
                        .where { ChannelTable.xtreamStreamId greater 1u }
                        .groupBy(ChannelTable.server, ChannelTable.xtreamStreamId)
                        .having { ChannelTable.xtreamStreamId.count() greater 1 }
                        .map { it[ChannelTable.id] }
                }
            } while (deleted > 0)
        }
    }

    companion object {
        private fun URI.extractStreamId(): String {
            if (null == this.toString().toChannelTypeOrNull()) return ""
            val streamId = this.toString().substringAfterLast("/", "").substringBeforeLast(".")
            return if (streamId.toDoubleOrNull() != null) streamId else ""
        }

        private fun ResultRow.toIptvChannel(): IptvChannel {
            val serversByName: IptvServersByName = getKoin().get()

            return IptvChannel(
                id = this[ChannelTable.id].value,
                externalPosition = this[ChannelTable.externalPosition],
                externalStreamId = this[ChannelTable.xtreamStreamId],
                server = serversByName[this[ChannelTable.server]]!!,
                name = this[ChannelTable.name],
                url = Url(this[ChannelTable.url]).toEncodedJavaURI(),
                epgId = this[ChannelTable.epgChannelId],
                logo = this[ChannelTable.icon],
                groups = this[ChannelTable.groups].toList(),
                catchupDays = this[ChannelTable.catchupDays]?.toInt() ?: 0,
                m3uProps = this[ChannelTable.m3uProps],
                vlcOpts = this[ChannelTable.vlcOpts],
                type = this[ChannelTable.type],
            )
        }

        private fun ResultRow.toXtreamLiveStream() = XtreamLiveStream(
            num = this[ChannelTable.externalPosition],
            name = this[ChannelTable.name],
            streamType = this[ChannelTable.type],
            typeName = this[ChannelTable.type],
            streamId = this[ChannelTable.id].value,
            streamIcon = this[ChannelTable.icon],
            thumbnail = null,
            epgChannelId = this[ChannelTable.epgChannelId],
            server = this[ChannelTable.server],
            added = "0",
            isAdult = 0u,
            categoryId = this[CategoryTable.id].value.toString(),
            categoryIds = listOf(this[CategoryTable.id].value),
            customSid = null,
            tvArchive = 0u,
            directSource = "",
            tvArchiveDuration = 0u,
        )
    }
}

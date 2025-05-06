package io.github.firstred.iptvproxy.db.repositories

import io.github.firstred.iptvproxy.classes.IptvChannel
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.tables.ChannelTable
import io.github.firstred.iptvproxy.db.tables.sources.PlaylistSourceTable
import io.github.firstred.iptvproxy.di.modules.IptvServersByName
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
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
                    keys = arrayOf(ChannelTable.server, ChannelTable.externalStreamId),
                    shouldReturnGeneratedValues = false,
                ) { channel ->
                    this[ChannelTable.server] = channel.server.name
                    this[ChannelTable.name] = channel.name
                    this[ChannelTable.url] = channel.url.toString()
                    this[ChannelTable.mainGroup] = channel.groups.firstOrNull()
                    this[ChannelTable.groups] = channel.groups.joinToString(",")
                    this[ChannelTable.type] = channel.type
                    this[ChannelTable.epgChannelId] = channel.epgId ?: ""
                    this[ChannelTable.externalStreamId] = channel.url.extractStreamId().toUInt()
                    this[ChannelTable.icon] = channel.logo
                    this[ChannelTable.catchupDays] = channel.catchupDays.toUInt()
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
                        externalStreamId = it[ChannelTable.externalStreamId],
                        server = serversByName[it[ChannelTable.server]]!!,
                        name = it[ChannelTable.name],
                        url = URI(it[ChannelTable.url]),
                        epgId = it[ChannelTable.epgChannelId],
                        logo = it[ChannelTable.icon],
                        groups = it[ChannelTable.groups]?.split(",")?.toList() ?: emptyList(),
                        catchupDays = it[ChannelTable.catchupDays]?.toInt() ?: 0,
                        type = it[ChannelTable.type],
                    )
                }.firstOrNull()
        }
    }

    fun forEachIptvChannelChunk(
        server: String? = null,
        sortedByName: Boolean = config.sortChannelsByName,
        chunkSize: Int = config.database.chunkSize.toInt(),
        action: (List<IptvChannel>) -> Unit,
    ) {
        var offset = 0L

        do {
            val channelQuery = ChannelTable.selectAll()
            server?.let { channelQuery.where { ChannelTable.server eq it } }
            if (sortedByName) {
                channelQuery.orderBy(ChannelTable.name to SortOrder.ASC)
            } else {
                channelQuery.orderBy(ChannelTable.server to SortOrder.ASC, ChannelTable.externalPosition to SortOrder.ASC)
            }
            channelQuery
                .limit(chunkSize)
                .offset(offset)
            val channels: List<IptvChannel> = transaction { channelQuery.map { it.toIptvChannel() } }

            if (channels.isEmpty()) break

            action(channels)
            offset += chunkSize
        } while (channels.isNotEmpty())
    }

    fun findInternalIdsByExternalIds(externalIds: List<UInt>, server: String) = transaction {
        ChannelTable
            .select(ChannelTable.externalStreamId, ChannelTable.id)
            .where { ChannelTable.externalStreamId inList externalIds.map { it } }
            .andWhere { ChannelTable.server eq server }
            .associateBy({ it[ChannelTable.externalStreamId] }, { it[ChannelTable.id].value })
    }

    fun getIptvChannelCount(): UInt = transaction {
        ChannelTable.selectAll().count().toUInt()
    }

    fun cleanup() {
        transaction {
            PlaylistSourceTable.deleteWhere {
                PlaylistSourceTable.server notInList config.servers.map { it.name }
            }

            ChannelTable.deleteWhere {
                ChannelTable.updatedAt less (Clock.System.now() - config.channelMaxStalePeriod)
            }
        }
    }

    companion object {
        private fun URI.extractStreamId(): String {
            return this.toString().substringAfterLast("/", "").substringBeforeLast(".")
        }

        private fun ResultRow.toIptvChannel(): IptvChannel {
            val serversByName: IptvServersByName = getKoin().get()

            return IptvChannel(
                id = this[ChannelTable.id].value,
                externalPosition = this[ChannelTable.externalPosition],
                externalStreamId = this[ChannelTable.externalStreamId],
                server = serversByName[this[ChannelTable.server]]!!,
                name = this[ChannelTable.name],
                url = URI(this[ChannelTable.url]),
                epgId = this[ChannelTable.epgChannelId],
                logo = this[ChannelTable.icon],
                groups = this[ChannelTable.groups]?.split(",")?.toList() ?: emptyList(),
                catchupDays = this[ChannelTable.catchupDays]?.toInt() ?: 0,
                type = this[ChannelTable.type],
            )
        }
    }
}

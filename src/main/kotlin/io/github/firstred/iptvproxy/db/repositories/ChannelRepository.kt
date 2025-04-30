package io.github.firstred.iptvproxy.db.repositories

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.tables.IptvChannelTable
import io.github.firstred.iptvproxy.db.tables.sources.PlaylistSourceTable
import io.github.firstred.iptvproxy.di.modules.IptvServersByName
import io.github.firstred.iptvproxy.entities.IptvChannel
import io.github.firstred.iptvproxy.plugins.withForeignKeyChecksDisabled
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.mp.KoinPlatform.getKoin
import java.net.URI
import kotlin.properties.Delegates

class ChannelRepository : KoinComponent {
    private val serversByName: IptvServersByName by inject()

    fun signalPlaylistStartedForServer(server: String) {
        transaction { withForeignKeyChecksDisabled {
            PlaylistSourceTable.upsert {
                it[PlaylistSourceTable.server] = server
                it[PlaylistSourceTable.startedAt] = Clock.System.now()
            } }
        }
    }
    fun signalPlaylistCompletedForServer(server: String) {
        transaction { withForeignKeyChecksDisabled {
            PlaylistSourceTable.upsert {
                it[PlaylistSourceTable.server] = server
                it[PlaylistSourceTable.completedAt] = Clock.System.now()
            } }
        }
    }

    fun upsertChannels(channels: List<IptvChannel>) {
        channels.chunked(config.database.chunkSize).forEach { chunk ->
            transaction { withForeignKeyChecksDisabled {
                chunk.forEach { channel ->
                    var id by Delegates.notNull<Long>()
                    IptvChannelTable.insertIgnore {
                        it[IptvChannelTable.server] = channel.server.name
                        it[IptvChannelTable.name] = channel.name
                        it[IptvChannelTable.url] = channel.url.toString()
                        it[IptvChannelTable.mainGroup] = channel.groups.firstOrNull()
                        it[IptvChannelTable.groups] = channel.groups.joinToString(",")
                        it[IptvChannelTable.type] = channel.type
                        it[IptvChannelTable.epgChannelId] = channel.epgId ?: ""
                        it[IptvChannelTable.externalStreamId] = channel.url.streamId()
                        it[IptvChannelTable.icon] = channel.logo
                        it[IptvChannelTable.catchupDays] = channel.catchupDays.toLong()
                    }.resultedValues?.firstOrNull()?.let { id = it[IptvChannelTable.id].value }
                    IptvChannelTable.update({ IptvChannelTable.id eq id }) {
                        it[IptvChannelTable.updatedAt] = Clock.System.now()
                    }
                }
            } }
        }
    }

    fun getChannelById(id: Long): IptvChannel? {
        return transaction {
            IptvChannelTable.select(IptvChannelTable.id)
                .where { IptvChannelTable.id eq id }
                .map {
                    IptvChannel(
                        id = it[IptvChannelTable.id].toString(),
                        server = serversByName[it[IptvChannelTable.server]]!!,
                        name = it[IptvChannelTable.name],
                        url = URI(it[IptvChannelTable.url]),
                        epgId = it[IptvChannelTable.epgChannelId],
                        logo = it[IptvChannelTable.icon],
                        groups = it[IptvChannelTable.groups]?.split(",")?.toList() ?: emptyList(),
                        catchupDays = it[IptvChannelTable.catchupDays]?.toInt() ?: 0,
                        type = it[IptvChannelTable.type],
                    )
                }.firstOrNull()
        }
    }


    fun forEachIptvChannelChunk(
        server: String? = null,
        sortedByName: Boolean = false,
        chunkSize: Int = config.database.chunkSize,
        action: (List<IptvChannel>) -> Unit,
    ) {
        var offset = 0L

        do {
            val channelQuery = IptvChannelTable.selectAll()
            server?.let { channelQuery.where { IptvChannelTable.server eq it } }
            if (sortedByName) channelQuery.orderBy(IptvChannelTable.name)
            channelQuery
                .limit(chunkSize)
                .offset(offset)
            val channels: List<IptvChannel> = transaction { channelQuery.map { it.toIptvChannel() } }

            if (channels.isEmpty()) break

            action(channels)
            offset += chunkSize
        } while (channels.isNotEmpty())
    }

    fun getIptvChannelCount(): Long = transaction {
        IptvChannelTable.selectAll().count()
    }

    fun cleanup() {
        transaction {
            PlaylistSourceTable.deleteWhere {
                PlaylistSourceTable.server notInList config.servers.map { it.name }
            }

            for (server in config.servers.map { it.name }) {
                try {
                    val (startedAt, completedAt) = PlaylistSourceTable
                        .select(PlaylistSourceTable.server eq server)
                        .map { Pair(it[PlaylistSourceTable.startedAt], it[PlaylistSourceTable.completedAt]) }
                        .first()
                    if (completedAt > startedAt) continue // Continue if the run hasn't finished (yet)

                    IptvChannelTable.deleteWhere {
                        IptvChannelTable.server eq server and
                                (IptvChannelTable.updatedAt less startedAt)
                    }
                } catch (_: NoSuchElementException) {
                }
            }
        }
    }

    companion object {
        private fun URI.streamId(): String? {
            return this.toString().substringAfterLast("/", "").substringBeforeLast(".")
        }

        private fun ResultRow.toIptvChannel(): IptvChannel {
            val serversByName: IptvServersByName = getKoin().get()

            return IptvChannel(
                id = this[IptvChannelTable.id].toString(),
                externalStreamId = this[IptvChannelTable.externalStreamId],
                server = serversByName[this[IptvChannelTable.server]]!!,
                name = this[IptvChannelTable.name],
                url = URI(this[IptvChannelTable.url]),
                epgId = this[IptvChannelTable.epgChannelId],
                logo = this[IptvChannelTable.icon],
                groups = this[IptvChannelTable.groups]?.split(",")?.toList() ?: emptyList(),
                catchupDays = this[IptvChannelTable.catchupDays]?.toInt() ?: 0,
                type = this[IptvChannelTable.type],
            )
        }
    }
}

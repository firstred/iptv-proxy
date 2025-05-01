package io.github.firstred.iptvproxy.db.tables

import io.github.firstred.iptvproxy.enums.IptvChannelType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object IptvChannelTable : LongIdTable("iptv_channel") {
    // id = streamId
    val epgChannelId: Column<String> = varchar("epg_channel_id", 511)
    val url: Column<String> = text("url")
    val externalStreamId: Column<String> = varchar("external_stream_id", 511)
    val server: Column<String> = varchar("server", 511)
    val icon: Column<String?> = text("icon").nullable()
    val name: Column<String> = text("name")
    val mainGroup: Column<String?> = text("main_group").nullable()
    val groups: Column<String?> = text("groups").nullable()
    val catchupDays: Column<Long?> = long("catchup_days").nullable()
    val type: Column<IptvChannelType> = enumerationByName(name = "type", length = 255, klass = IptvChannelType::class)
    val createdAt: Column<Instant> = timestamp("created_at").default(Clock.System.now())
    val updatedAt: Column<Instant> = timestamp("updated_at").default(Clock.System.now())

    init {
        uniqueIndex(server, url)
        index(false, server, externalStreamId)
    }
}

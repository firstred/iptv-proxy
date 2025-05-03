package io.github.firstred.iptvproxy.db.tables

import io.github.firstred.iptvproxy.enums.IptvChannelType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object IptvChannelTable : LongIdTable("iptv_channel") {
    // id = streamId   // Internal stream ID, used for HLS URLs
    val externalIndex: Column<Long> = long("external_index").default(0L) // Used to retain external sort order - should be the same order as num from Xtream API
    val epgChannelId: Column<String> = varchar("epg_channel_id", 255)          // Link with EPG
    val url: Column<String> = text("url")
    val externalStreamId: Column<String> = varchar("external_stream_id", 255)  // External stream ID, used for requests to external servers
    val server: Column<String> = varchar("server", 255)
    val icon: Column<String?> = text("icon").nullable()
    val name: Column<String> = text("name")
    val mainGroup: Column<String?> = text("main_group").nullable()
    val groups: Column<String?> = text("groups").nullable()
    val catchupDays: Column<Long?> = long("catchup_days").nullable()
    val type: Column<IptvChannelType> = enumerationByName(name = "type", length = 255, klass = IptvChannelType::class)
    val createdAt: Column<Instant> = timestamp("created_at").default(Clock.System.now())
    val updatedAt: Column<Instant> = timestamp("updated_at").default(Clock.System.now())

    init {
        index(
            customIndexName = "idx_kusrhyzuxpyu5uxjz3j",
            isUnique = false,
            externalIndex,
            server,
        )
        index(
            customIndexName = "idx_kljsadfh08sdfh8sdfh8sdf",
            isUnique = false,
            server,
            externalStreamId,
        )
        uniqueIndex(
            customIndexName = "unq_hsdf7sadfh8sdfh8sdf",
            server,
            url,
        )
    }
}

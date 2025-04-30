package io.github.firstred.iptvproxy.db.tables.channels

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object LiveStreamTable : Table("live_stream") {
    val num: Column<Long> = long("num")
    val server: Column<String> = varchar("server", 511)
    val name: Column<String> = text("name")
    val streamId: Column<Long?> = long("stream_id").nullable()
    val externalStreamId: Column<String> = varchar("external_stream_id", 511)
    val icon: Column<String?> = text("icon").nullable()
    val epgChannelId: Column<String?> = varchar("epg_channel_id", 511).nullable()
    val added: Column<String> = varchar("added", 511).default("0")
    val isAdult: Column<Int> = integer("is_adult").default(0)
    val mainCategoryId: Column<Long?> = long("main_category_id").nullable()
    val customSid: Column<String?> = varchar("custom_sid", 511).nullable()
    val tvArchive: Column<Int> = integer("tv_archive").default(0)
    val directSource: Column<String> = varchar("direct_source", 511).default("")
    val tvArchiveDuration: Column<Int> = integer("tv_archive_duration").default(0)
    val createdAt: Column<Instant> = timestamp("created_at").default(Clock.System.now())
    val updatedAt: Column<Instant> = timestamp("updated_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(arrayOf(server, num))

    init {
        uniqueIndex(server, externalStreamId)
        index(false, server, streamId)
    }
}

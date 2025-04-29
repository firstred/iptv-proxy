package io.github.firstred.iptvproxy.db.tables.channels

import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object LiveStreamTable : Table("live_stream") {
    val num: Column<Int> = integer("num")
    val server: Column<String> = varchar("server", 511)
    val name: Column<String> = text("name")
    val streamId: Column<String> = varchar("stream_id", 511)
    val streamIcon: Column<String?> = varchar("stream_icon", 2047).nullable()
    val epgChannelId: Column<String?> = varchar("epg_channel_id", 511).nullable()
    val added: Column<Instant> = timestamp("added")
    val isAdult: Column<Int> = integer("is_adult").default(0)
    val mainCategoryId: Column<String> = varchar("main_category_id", 511)
    val customSid: Column<String?> = varchar("custom_sid", 511).nullable()
    val tvArchive: Column<Int> = integer("tv_archive")
    val directSource: Column<String> = varchar("direct_source", 511)
    val tvArchiveDuration: Column<Int> = integer("tv_archive_duration")

    override val primaryKey = PrimaryKey(arrayOf(server, num))
}

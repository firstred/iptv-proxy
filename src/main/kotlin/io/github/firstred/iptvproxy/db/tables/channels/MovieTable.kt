package io.github.firstred.iptvproxy.db.tables.channels

import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object MovieTable : Table("movie") {
    val num: Column<Int> = integer("num")
    val server: Column<String> = varchar("server", 511)
    val name: Column<String> = text("name")
    val streamId: Column<String> = varchar("stream_id", 511)
    val streamIcon: Column<String?> = varchar("stream_icon", 2047).nullable()
    val rating: Column<String?> = varchar("rating", 511).nullable()
    val rating5Based: Column<Double?> = double("rating_5based").nullable()
    val tmdb: Column<String?> = varchar("tmdb", 511).nullable()
    val trailer: Column<String?> = varchar("trailer", 511).nullable()
    val added: Column<Instant?> = timestamp("added").nullable()
    val isAdult = integer("is_adult").default(0)
    val mainCategoryId: Column<String> = varchar("main_category_id", 511)
    val containerExtension: Column<String> = varchar("container_extension", 511)
    val customSid: Column<String?> = varchar("custom_sid", 511).nullable()
    val directSource: Column<String?> = text("direct_source").nullable()

    override val primaryKey = PrimaryKey(arrayOf(server, num))
}

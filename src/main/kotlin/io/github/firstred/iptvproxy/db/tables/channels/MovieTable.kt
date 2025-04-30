package io.github.firstred.iptvproxy.db.tables.channels

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object MovieTable : Table("movie") {
    val num: Column<Long> = long("num")
    val server: Column<String> = varchar("server", 511)
    val name: Column<String> = text("name")
    val streamId: Column<Long?> = long("stream_id").nullable() // From IptvChannel
    val externalStreamId: Column<String> = varchar("external_stream_id", 511)
    val externalStreamIcon: Column<String?> = text("external_stream_icon").nullable()
    val rating: Column<String?> = varchar("rating", 511).nullable()
    val rating5Based: Column<Double?> = double("rating_5based").nullable()
    val tmdb: Column<String?> = varchar("tmdb", 511).nullable()
    val trailer: Column<String?> = varchar("trailer", 511).nullable()
    val added: Column<String> = varchar("added", 511).default("0")
    val isAdult = integer("is_adult").default(0)
    val mainCategoryId: Column<Long?> = long("main_category_id").nullable()
    val containerExtension: Column<String> = varchar("container_extension", 511)
    val customSid: Column<String?> = varchar("custom_sid", 511).nullable()
    val directSource: Column<String?> = text("direct_source").nullable()

    override val primaryKey = PrimaryKey(arrayOf(server, num))

    init {
        uniqueIndex(server, externalStreamId)
        index(false, server, streamId)
    }
}

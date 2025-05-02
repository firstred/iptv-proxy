package io.github.firstred.iptvproxy.db.tables.channels

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object MovieTable : Table("movie") {
    val num: Column<Long> = long("num")
    val server: Column<String> = varchar("server", 255)
    val name: Column<String> = text("name")
    val externalStreamId: Column<String> = varchar("external_stream_id", 255)
    val externalStreamIcon: Column<String?> = text("external_stream_icon").nullable()
    val rating: Column<String?> = varchar("rating", 255).nullable()
    val rating5Based: Column<Double?> = double("rating_5based").nullable()
    val tmdb: Column<String?> = varchar("tmdb", 255).nullable()
    val trailer: Column<String?> = varchar("trailer", 255).nullable()
    val added: Column<String> = varchar("added", 255).default("0")
    val isAdult = integer("is_adult").default(0)
    val mainCategoryId: Column<Long?> = long("main_category_id").nullable()
    val containerExtension: Column<String> = varchar("container_extension", 255)
    val customSid: Column<String?> = varchar("custom_sid", 255).nullable()
    val directSource: Column<String?> = text("direct_source").nullable()
    val createdAt: Column<Instant> = timestamp("created_at").default(Clock.System.now())
    val updatedAt: Column<Instant> = timestamp("updated_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(arrayOf(server, num))

    init {
        uniqueIndex(
            customIndexName = "unq_oidfhjasdufiisdfh8sdf",
            server,
            externalStreamId,
        )
    }
}

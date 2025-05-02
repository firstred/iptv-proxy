package io.github.firstred.iptvproxy.db.tables.epg

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgChannelTable : Table("epg_channel") {
    val epgChannelId: Column<String> = varchar("epg_channel_id", 255)
    val server: Column<String> = varchar("server", 255)
    val icon: Column<String?> = text("icon").nullable()
    val name: Column<String> = varchar("name", 511)
    val createdAt: Column<Instant> = timestamp("created_at").default(Clock.System.now())
    val updatedAt: Column<Instant> = timestamp("updated_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(arrayOf(server, epgChannelId))

    init {
        index(
            customIndexName = "idx_hCn36jexzfx9cipkfy0l",
            isUnique = false,
            server,
            name,
        )
    }
}

package io.github.firstred.iptvproxy.db.tables.epg

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object EpgChannelDisplayNameTable : Table("epg_display_name") {
    val epgChannelId: Column<String> = varchar("epg_channel_id", 511)
    val server: Column<String> = varchar("server", 511)
    val language: Column<String> = varchar("language", 511)
    val name: Column<String> = text("name")

    override val primaryKey = PrimaryKey(arrayOf(epgChannelId, language))

    init {
        foreignKey(
            epgChannelId to EpgChannelTable.epgChannelId,
            server to EpgChannelTable.server,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
        )
    }
}

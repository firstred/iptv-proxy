package io.github.firstred.iptvproxy.db.tables.epg

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object EpgChannelTable : Table("epg_channel") {
    val epgChannelId: Column<String> = varchar("epg_channel_id", 511)
    val server: Column<String> = varchar("server", 511)
    val icon: Column<String?> = text("icon").nullable()
    val name: Column<String> = text("name")

    override val primaryKey = PrimaryKey(arrayOf(server, epgChannelId))

    init {
        index(isUnique = false, server, name)
    }
}

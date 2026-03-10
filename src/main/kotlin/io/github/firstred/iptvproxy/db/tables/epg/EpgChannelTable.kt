package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgChannelTable : Table("epg_channel") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val icon = text("icon").nullable()
    val name = varchar("name", 511)
    val createdAt: Column<Instant> = timestamp("created_at").default(Clock.System.now())
    val updatedAt: Column<Instant> = timestamp("updated_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(epgChannelId)
}

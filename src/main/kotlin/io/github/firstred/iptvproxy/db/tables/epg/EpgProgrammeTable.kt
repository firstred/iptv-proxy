package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammeTable : Table("epg_programme") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val start: Column<Instant> = timestamp("start")
    val stop: Column<Instant> = timestamp("stop")
    val title = text("title")
    val subtitle = text("subtitle")
    val description = text("description")
    val icon = text("icon").nullable()
    val createdAt: Column<Instant> = timestamp("created_at").default(Clock.System.now())
    val updatedAt: Column<Instant> = timestamp("updated_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(arrayOf(epgChannelId, start))
}

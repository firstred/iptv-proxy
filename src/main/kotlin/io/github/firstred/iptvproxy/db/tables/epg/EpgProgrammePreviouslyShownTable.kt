package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammePreviouslyShownTable : Table("epg_programme_previously_shown") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart: Column<Instant> = timestamp("programme_start")
    val previousStart: Column<Instant> = timestamp("previous_start")

    override val primaryKey = PrimaryKey(arrayOf(epgChannelId, programmeStart))
}

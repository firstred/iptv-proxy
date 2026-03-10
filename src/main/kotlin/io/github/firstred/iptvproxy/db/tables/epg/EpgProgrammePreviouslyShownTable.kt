package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object EpgProgrammePreviouslyShownTable : Table("epg_programme_previously_shown") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart: Column<Instant> = timestamp("programme_start")
    val previousStart: Column<Instant> = timestamp("previous_start")

    override val primaryKey = PrimaryKey(arrayOf(epgChannelId, programmeStart))
}

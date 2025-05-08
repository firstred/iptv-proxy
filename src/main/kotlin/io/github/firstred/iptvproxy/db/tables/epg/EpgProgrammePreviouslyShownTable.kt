package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammePreviouslyShownTable : Table("epg_programme_previously_shown") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart = timestamp("programme_start")
    val previousStart = timestamp("previous_start")

    override val primaryKey = PrimaryKey(arrayOf(epgChannelId, programmeStart))
}

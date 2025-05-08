package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammeRatingTable : Table("epg_programme_rating") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart = timestamp("programme_start")
    val system = varchar("system", defaultVarcharLength)
    val rating = varchar("rating", defaultVarcharLength)

    override val primaryKey = PrimaryKey(arrayOf(epgChannelId, programmeStart, system))
}

package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammeEpisodeNumberTable : Table("epg_programme_episode_number") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart = timestamp("programme_start")
    val system = varchar("system", defaultVarcharLength).nullable()
    val number = varchar("number", defaultVarcharLength)

    override val primaryKey = PrimaryKey(arrayOf(epgChannelId, programmeStart, system))
}

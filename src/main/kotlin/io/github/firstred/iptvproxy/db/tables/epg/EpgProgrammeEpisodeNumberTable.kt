package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object EpgProgrammeEpisodeNumberTable : Table("epg_programme_episode_number") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart: Column<Instant> = timestamp("programme_start")
    val system = varchar("system", defaultVarcharLength).nullable()
    val number = varchar("number", defaultVarcharLength)

    override val primaryKey = PrimaryKey(arrayOf(epgChannelId, programmeStart, system))
}

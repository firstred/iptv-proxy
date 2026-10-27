package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object EpgProgrammeAudioTable : Table("epg_programme_audio") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart: Column<Instant> = timestamp("programme_start")
    val type = varchar("type", defaultVarcharLength)
    val value = varchar("value", defaultVarcharLength)

    override val primaryKey = PrimaryKey(arrayOf(epgChannelId, programmeStart, type))
}

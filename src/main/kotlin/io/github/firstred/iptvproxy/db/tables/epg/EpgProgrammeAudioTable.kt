package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammeAudioTable : Table("epg_programme_audio") {
    val server = varchar("server", maxServerNameLength)
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart = timestamp("programme_start")
    val type = varchar("type", defaultVarcharLength)
    val value = varchar("value", defaultVarcharLength)

    override val primaryKey = PrimaryKey(arrayOf(server, epgChannelId, programmeStart, type))

    init {
        foreignKey(
            server to EpgProgrammeTable.server,
            epgChannelId to EpgProgrammeTable.epgChannelId,
            programmeStart to EpgProgrammeTable.start,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_7ee6751eace2c92dd41471ef75762c3b",
        )
    }
}

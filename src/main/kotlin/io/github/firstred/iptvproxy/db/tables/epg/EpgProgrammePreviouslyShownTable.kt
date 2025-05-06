package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.dtos.config.defaultVarcharLength
import io.github.firstred.iptvproxy.dtos.config.maxServerNameDbLength
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammePreviouslyShownTable : Table("epg_programme_previously_shown") {
    val server = varchar("server", maxServerNameDbLength)
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart = timestamp("programme_start")
    val previousStart = timestamp("previous_start")

    override val primaryKey = PrimaryKey(arrayOf(server, epgChannelId, programmeStart))

    init {
        foreignKey(
            server to EpgProgrammeTable.server,
            epgChannelId to EpgProgrammeTable.epgChannelId,
            programmeStart to EpgProgrammeTable.start,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_0752370f810c318fea40c956f446d991",
        )
    }
}

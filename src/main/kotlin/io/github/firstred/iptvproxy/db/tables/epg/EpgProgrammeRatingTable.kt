package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.dtos.config.defaultVarcharLength
import io.github.firstred.iptvproxy.dtos.config.maxServerNameDbLength
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammeRatingTable : Table("epg_programme_rating") {
    val server = varchar("server", maxServerNameDbLength)
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart = timestamp("programme_start")
    val system = varchar("system", defaultVarcharLength)
    val rating = varchar("rating", defaultVarcharLength)

    override val primaryKey = PrimaryKey(arrayOf(server, epgChannelId, programmeStart, system))

    init {
        foreignKey(
            server to EpgProgrammeTable.server,
            programmeStart to EpgProgrammeTable.start,
            epgChannelId to EpgProgrammeTable.epgChannelId,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_8afc876ec06fab3a423b20b788e2a021",
        )
    }
}

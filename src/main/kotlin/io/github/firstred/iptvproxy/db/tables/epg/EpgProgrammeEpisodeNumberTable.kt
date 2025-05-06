package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammeEpisodeNumberTable : Table("epg_programme_episode_number") {
    val server = varchar("server", maxServerNameLength)
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart = timestamp("programme_start")
    val system = varchar("system", defaultVarcharLength).nullable()
    val number = varchar("number", defaultVarcharLength)

    override val primaryKey = PrimaryKey(arrayOf(server, epgChannelId, programmeStart, system))

    init {
        foreignKey(
            server to EpgProgrammeTable.server,
            epgChannelId to EpgProgrammeTable.epgChannelId,
            programmeStart to EpgProgrammeTable.start,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_c3ffdceccaf6c3c11ba9b56b173c3d6e",
        )
    }
}

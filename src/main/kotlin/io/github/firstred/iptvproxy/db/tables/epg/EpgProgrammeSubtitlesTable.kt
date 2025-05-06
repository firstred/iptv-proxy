package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammeSubtitlesTable : Table("epg_programme_subtitles") {
    val server = varchar("server", maxServerNameLength)
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart = timestamp("programme_start")
    val language = varchar("language", defaultVarcharLength)
    val subtitle = text("subtitle")

    override val primaryKey = PrimaryKey(arrayOf(server, epgChannelId, programmeStart, language))

    init {
        foreignKey(
            server to EpgProgrammeTable.server,
            epgChannelId to EpgProgrammeTable.epgChannelId,
            programmeStart to EpgProgrammeTable.start,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_c46efb630ad43c18420fc4359bb1dbcd",
        )
    }
}

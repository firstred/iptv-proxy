package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.dtos.config.defaultVarcharLength
import io.github.firstred.iptvproxy.dtos.config.maxServerNameDbLength
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammeCategoryTable : Table("epg_programme_category") {
    val server = varchar("server", maxServerNameDbLength)
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart = timestamp("programme_start")
    val language = varchar("language", defaultVarcharLength)
    val category = varchar("category", defaultVarcharLength)

    override val primaryKey = PrimaryKey(arrayOf(server, epgChannelId, programmeStart, language))

    init {
        foreignKey(
            server to EpgProgrammeTable.server,
            epgChannelId to EpgProgrammeTable.epgChannelId,
            programmeStart to EpgProgrammeTable.start,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_90938e49cb36faa81068c23c77ce0397",
        )
    }
}

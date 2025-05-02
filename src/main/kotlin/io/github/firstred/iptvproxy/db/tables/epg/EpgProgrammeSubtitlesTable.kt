package io.github.firstred.iptvproxy.db.tables.epg

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object EpgProgrammeSubtitlesTable : Table("epg_programme_subtitles") {
    val programmeId: Column<Long> = long("programme_id")
    val server: Column<String> = varchar("server", 255)
    val type: Column<String> = varchar("type", 255)

    override val primaryKey = PrimaryKey(arrayOf(programmeId, type))

    init {
        foreignKey(
            programmeId to EpgProgrammeTable.id,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_lkjhs45678sdfgdf",
        )
    }
}

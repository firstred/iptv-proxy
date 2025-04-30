package io.github.firstred.iptvproxy.db.tables.epg

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object EpgProgrammeEpisodeNumberTable : Table("epg_programme_episode_number") {
    val programmeId: Column<Long> = long("programme_id")
    val server: Column<String> = varchar("server", 511)
    val system: Column<String?> = varchar("system", 511).nullable()
    val number: Column<String> = varchar("number", 511)

    override val primaryKey = PrimaryKey(arrayOf(programmeId, system))

    init {
        foreignKey(
            programmeId to EpgProgrammeTable.id,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE
        )
    }
}

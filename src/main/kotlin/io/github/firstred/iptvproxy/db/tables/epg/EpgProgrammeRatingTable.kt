package io.github.firstred.iptvproxy.db.tables.epg

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object EpgProgrammeRatingTable : Table("epg_programme_rating") {
    val programmeId: Column<Long> = long("programme_id")
    val server: Column<String> = varchar("server", 255)
    val system: Column<String> = varchar("system", 255)
    val rating: Column<String> = varchar("rating", 255)

    override val primaryKey = PrimaryKey(arrayOf(programmeId, system))

    init {
        foreignKey(
            programmeId to EpgProgrammeTable.id,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_plasdjolkjsmgjdfg",
        )
    }
}

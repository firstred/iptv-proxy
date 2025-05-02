package io.github.firstred.iptvproxy.db.tables.epg

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object EpgProgrammeCategoryTable : Table("epg_programme_category") {
    val programmeId: Column<Long> = long("programme_id")
    val server: Column<String> = varchar("server", 255)
    val language: Column<String> = varchar("language", 255)
    val category: Column<String> = varchar("category", 255)

    override val primaryKey = PrimaryKey(arrayOf(programmeId, language, category))

    init {
        foreignKey(
            programmeId to EpgProgrammeTable.id,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_lakdfjh8sdfh8sdfh8sdf",
        )
    }
}

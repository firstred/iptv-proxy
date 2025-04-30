package io.github.firstred.iptvproxy.db.tables.epg

import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammePreviouslyShownTable : Table("epg_programme_previously_shown") {
    val programmeId: Column<Long> = long("programme_id")
    val server: Column<String> = varchar("server", 511)
    val start: Column<Instant> = timestamp("start")

    override val primaryKey = PrimaryKey(arrayOf(programmeId, start))

    init {
        foreignKey(
            programmeId to EpgProgrammeTable.id,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE
        )
    }
}

package io.github.firstred.iptvproxy.db.tables.sources

import io.github.firstred.iptvproxy.utils.maxServerNameLength
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object XtreamSourceTable : Table("xtream_source") {
    val server = varchar("server", maxServerNameLength)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val startedImportAt = timestamp("started_import_at").default(Clock.System.now())
    val completedImportAt = timestamp("completed_import_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(arrayOf(server)) // Only one source per server
}

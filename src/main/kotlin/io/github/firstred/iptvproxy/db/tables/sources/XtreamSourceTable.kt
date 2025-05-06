package io.github.firstred.iptvproxy.db.tables.sources

import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object XtreamSourceTable : Table("xtream_source") {
    val server = varchar("server", maxServerNameLength)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val startedImportAt = timestamp("started_import_at").defaultExpression(CurrentTimestamp)
    val completedImportAt = timestamp("completed_import_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(arrayOf(server)) // Only one source per server
}

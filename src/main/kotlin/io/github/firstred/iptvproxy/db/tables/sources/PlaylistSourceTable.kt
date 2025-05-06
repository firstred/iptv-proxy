package io.github.firstred.iptvproxy.db.tables.sources

import io.github.firstred.iptvproxy.dtos.config.maxServerNameDbLength
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object PlaylistSourceTable : Table("playlist_source") {
    val server = varchar("server", maxServerNameDbLength)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val startedImportAt = timestamp("started_import_at").defaultExpression(CurrentTimestamp)
    val completedImportAt = timestamp("completed_import_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(arrayOf(server)) // Only one source per server
}

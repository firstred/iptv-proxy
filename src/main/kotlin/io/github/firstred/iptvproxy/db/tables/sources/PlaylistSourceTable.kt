package io.github.firstred.iptvproxy.db.tables.sources

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object PlaylistSourceTable : Table("playlist_source") {
    val server: Column<String> = varchar("server", 511)
    val createdAt: Column<Instant> = timestamp("created_at").default(Clock.System.now())
    val startedAt: Column<Instant> = timestamp("started_at").default(Clock.System.now())
    val completedAt: Column<Instant> = timestamp("completed_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(arrayOf(server)) // Only one source per server
}

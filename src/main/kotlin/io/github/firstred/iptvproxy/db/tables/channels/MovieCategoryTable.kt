package io.github.firstred.iptvproxy.db.tables.channels

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object MovieCategoryTable : LongIdTable("movie_category") {
    val server: Column<String> = varchar("server", 255)
    val externalCategoryId: Column<Long> = long("external_category_id")
    val name: Column<String> = text("category_name")
    val parentId: Column<String> = varchar("parent_id", 255).default("0")
    val createdAt: Column<Instant> = timestamp("created_at").default(Clock.System.now())
    val updatedAt: Column<Instant> = timestamp("updated_at").default(Clock.System.now())

    init {
        uniqueIndex(server, externalCategoryId)
    }
}

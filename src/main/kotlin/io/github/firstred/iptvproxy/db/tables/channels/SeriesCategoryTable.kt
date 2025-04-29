package io.github.firstred.iptvproxy.db.tables.channels

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object SeriesCategoryTable : Table("series_category") {
    val server: Column<String> = varchar("server", 511)
    val categoryId: Column<String> = varchar("category_id", 511)
    val name: Column<String> = text("category_name")
    val parentId: Column<String> = varchar("parent_id", 511).default("0")

    override val primaryKey = PrimaryKey(arrayOf(server, categoryId))

    init {
        foreignKey(
            server to SeriesTable.server,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE
        )
    }
}

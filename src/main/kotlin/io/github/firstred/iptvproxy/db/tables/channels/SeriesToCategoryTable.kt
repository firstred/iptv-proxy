package io.github.firstred.iptvproxy.db.tables.channels

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object SeriesToCategoryTable : Table("series_to_category") {
    val server: Column<String> = varchar("server", 511)
    val num: Column<Int> = integer("num")
    val categoryId: Column<Int> = integer("category_id")

    override val primaryKey = PrimaryKey(arrayOf(server, num, categoryId))

    init {
        foreignKey(
            server to SeriesTable.server,
            num to SeriesTable.num,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
        )
        foreignKey(
            server to SeriesCategoryTable.server,
            categoryId to SeriesCategoryTable.categoryId,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
        )
    }
}

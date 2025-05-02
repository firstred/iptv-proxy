package io.github.firstred.iptvproxy.db.tables.channels

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object SeriesToCategoryTable : Table("series_to_category") {
    val server: Column<String> = varchar("server", 255)
    val num: Column<Long> = long("num")
    val categoryId: Column<Long> = long("category_id")

    override val primaryKey = PrimaryKey(arrayOf(server, num, categoryId))

    init {
        foreignKey(
            server to SeriesTable.server,
            num to SeriesTable.num,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name ="fk_pohnbmdv8sdfh8sdfh8sdf",
        )
        foreignKey(
            server to SeriesCategoryTable.server,
            categoryId to SeriesCategoryTable.externalCategoryId,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_sfdoijgj8sdfh8sdfh8sdf",
        )
    }
}

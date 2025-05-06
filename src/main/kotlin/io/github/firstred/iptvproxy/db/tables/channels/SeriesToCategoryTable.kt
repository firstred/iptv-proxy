package io.github.firstred.iptvproxy.db.tables.channels

import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object SeriesToCategoryTable : Table("series_to_category") {
    val server = varchar("server", maxServerNameLength)
    val externalSeriesId = uinteger("external_series_id")
    val categoryId = uinteger("category_id")

    override val primaryKey = PrimaryKey(arrayOf(server, externalSeriesId, categoryId))

    init {
        foreignKey(
            server to SeriesTable.server,
            externalSeriesId to SeriesTable.externalSeriesId,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name ="fk_de4eac24e8e0ac2a832af0ae75b5467c",
        )
        foreignKey(
            server to CategoryTable.server,
            categoryId to CategoryTable.externalCategoryId,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_8e1ea5142dfbdedb40a061ad82154727",
        )
    }
}

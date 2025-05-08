package io.github.firstred.iptvproxy.db.tables.channels

import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.Table

object SeriesToCategoryTable : Table("series_to_category") {
    val server = varchar("server", maxServerNameLength)
    val externalSeriesId = uinteger("external_series_id")
    val categoryId = uinteger("category_id")

    override val primaryKey = PrimaryKey(arrayOf(server, externalSeriesId, categoryId))
}

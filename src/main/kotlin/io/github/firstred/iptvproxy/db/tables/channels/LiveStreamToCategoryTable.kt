package io.github.firstred.iptvproxy.db.tables.channels

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object LiveStreamToCategoryTable : Table("live_stream_to_category") {
    val server: Column<String> = varchar("server", 511)
    val num: Column<Long> = long("num")
    val categoryId: Column<Long> = long("category_id")

    override val primaryKey = PrimaryKey(arrayOf(server, num, categoryId))

    init {
        foreignKey(
            server to LiveStreamTable.server,
            num to LiveStreamTable.num,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
        )
        foreignKey(
            categoryId to LiveStreamCategoryTable.id,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
        )
    }
}

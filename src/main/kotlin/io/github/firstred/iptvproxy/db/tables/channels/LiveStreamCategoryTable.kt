package io.github.firstred.iptvproxy.db.tables.channels

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption

object LiveStreamCategoryTable : LongIdTable("live_stream_category") {
    val server: Column<String> = varchar("server", 511)
    val externalCategoryId: Column<Long> = long("external_category_id")
    val name: Column<String> = text("category_name")
    val parentId: Column<String> = varchar("parent_id", 511).default("0")

    init {
        uniqueIndex(server, externalCategoryId)

        foreignKey(
            server to LiveStreamTable.server,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE
        )
    }
}

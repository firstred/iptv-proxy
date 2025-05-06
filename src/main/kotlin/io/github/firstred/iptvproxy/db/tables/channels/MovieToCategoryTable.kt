package io.github.firstred.iptvproxy.db.tables.channels

import io.github.firstred.iptvproxy.dtos.config.maxServerNameDbLength
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object MovieToCategoryTable : Table("movie_to_category") {
    val server = varchar("server", maxServerNameDbLength)
    val externalStreamId = uinteger("external_stream_id")
    val categoryId = uinteger("category_id")

    override val primaryKey = PrimaryKey(arrayOf(server, externalStreamId, categoryId))

    init {
        foreignKey(
            server to MovieTable.server,
            externalStreamId to MovieTable.externalStreamId,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_2b008c191bfbbd2aa631b3139f3979cc",
        )
        foreignKey(
            categoryId to CategoryTable.id,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_bf79cb5f218687462a48b89ea961ead3",
        )
    }
}

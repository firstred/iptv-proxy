package io.github.firstred.iptvproxy.db.tables.channels

import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object LiveStreamToCategoryTable : Table("live_stream_to_category") {
    val server = varchar("server", maxServerNameLength)
    val externalStreamId = uinteger("external_stream_id")
    val categoryId = uinteger("category_id")

    override val primaryKey = PrimaryKey(arrayOf(server, externalStreamId, categoryId))

    init {
        foreignKey(
            server to LiveStreamTable.server,
            externalStreamId to LiveStreamTable.externalStreamId,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_58fef7282f0ac2d4a48ccd925004370f",
        )
        foreignKey(
            categoryId to CategoryTable.id,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_8343e7d497dc8ffdbcc4ed31a3e66141",
        )
    }
}

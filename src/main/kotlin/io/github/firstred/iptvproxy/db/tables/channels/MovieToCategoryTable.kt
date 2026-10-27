package io.github.firstred.iptvproxy.db.tables.channels

import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.Table

object MovieToCategoryTable : Table("movie_to_category") {
    val server = varchar("server", maxServerNameLength)
    val externalStreamId = uinteger("external_stream_id")
    val categoryId = uinteger("category_id")

    override val primaryKey = PrimaryKey(arrayOf(server, externalStreamId, categoryId))
}

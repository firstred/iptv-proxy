package io.github.firstred.iptvproxy.db.tables.channels

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object MovieTable : Table("movie") {
    val num = uinteger("num").default(0u)
    val server = varchar("server", maxServerNameLength)
    val name = text("name")
    val externalStreamId = uinteger("external_stream_id")
    val externalStreamIcon = text("external_stream_icon").nullable()
    val rating = varchar("rating", defaultVarcharLength).nullable()
    val rating5Based = float("rating_5based").nullable()
    val tmdb = uinteger("tmdb").nullable()
    val trailer = varchar("trailer", defaultVarcharLength).nullable()
    val added = timestamp("added").defaultExpression(CurrentTimestamp)
    val isAdult = bool("is_adult").default(false)
    val mainCategoryId = uinteger("main_category_id").nullable()
    val containerExtension = varchar("container_extension", 16)
    val customSid = varchar("custom_sid", defaultVarcharLength).nullable()
    val directSource = text("direct_source").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(arrayOf(server, externalStreamId))

    init {
        index(
            customIndexName = "idx_8aa06220d55032bf3701990defb691b6",
            isUnique = false,
            server,
            num,
        )
    }
}

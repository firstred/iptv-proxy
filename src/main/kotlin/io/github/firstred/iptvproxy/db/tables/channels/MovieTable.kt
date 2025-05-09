package io.github.firstred.iptvproxy.db.tables.channels

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Table
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
    val youtubeTrailer = varchar("youtube_trailer", defaultVarcharLength).nullable()
    val added = timestamp("added").default(Clock.System.now())
    val isAdult = bool("is_adult").default(false)
    val mainCategoryId = uinteger("main_category_id").nullable()
    val containerExtension = varchar("container_extension", 16)
    val customSid = varchar("custom_sid", defaultVarcharLength).nullable()
    val directSource = text("direct_source").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())

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

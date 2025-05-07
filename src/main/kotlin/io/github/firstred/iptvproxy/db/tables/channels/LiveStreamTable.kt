package io.github.firstred.iptvproxy.db.tables.channels

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object LiveStreamTable : Table("live_stream") {
    val num = uinteger("num").default(0u)
    val server = varchar("server", maxServerNameLength)
    val name = text("name")
    val externalStreamId  = uinteger("external_stream_id")
    val icon = text("icon").nullable()
    val thumbnail = text("thumbnail").nullable()
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength).nullable()
    val added = timestamp("added").defaultExpression(CurrentTimestamp)
    val isAdult = bool("is_adult").default(false)
    val mainCategoryId = uinteger("main_category_id").nullable()
    val customSid = varchar("custom_sid", defaultVarcharLength).nullable()
    val tvArchive = bool("tv_archive").default(false)
    val directSource = varchar("direct_source", defaultVarcharLength).default("")
    val tvArchiveDuration = uinteger("tv_archive_duration").default(0u)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(arrayOf(server, externalStreamId))

    init {
        uniqueIndex(server, externalStreamId)
        index(
            customIndexName = "idx_6c03df766175249bd3424a88c18fa99e",
            isUnique = false,
            server,
            num,
        )
    }
}

package io.github.firstred.iptvproxy.db.tables

import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.dao.id.UIntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object ChannelTable : UIntIdTable("channel") {
    // id = streamId   // Internal stream ID, used for HLS URLs
    val externalPosition = uinteger("external_position") // External playlist position
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength) // Link with EPG
    val url = text("url")
    val xtreamStreamId = uinteger("xtream_stream_id").nullable() // External stream ID, used for requests to external servers
    val server = varchar("server", maxServerNameLength)
    val icon = text("icon").nullable()
    val name = text("name")
    val mainGroup = text("main_group").nullable()
    val groups = text("groups").nullable()
    val catchupDays = uinteger("catchup_days").nullable()
    val type = enumerationByName(name = "type", length = IptvChannelType.maxDbLength, klass = IptvChannelType::class)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    init {
        index(
            customIndexName = "idx_81e0d67366a2ec3bed4ad79abd8f8940",
            isUnique = false,
            server,
            externalPosition,
        )
        uniqueIndex(
            customIndexName = "uniq_037df95408f7de3c48f8ff391f86c3df",
            server,
            xtreamStreamId,
        )
    }
}

package io.github.firstred.iptvproxy.db.tables

import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.dao.id.UIntIdTable
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import io.github.firstred.iptvproxy.serialization.json as jsonSerializer

object ChannelTable : UIntIdTable("channel") {
    // id = streamId   // Internal stream ID, used for HLS URLs
    val externalPosition = uinteger("external_position") // External playlist position
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength).nullable() // Link with EPG
    val url = text("url")
    val xtreamStreamId = uinteger("xtream_stream_id").default(0u) // External stream ID, used for requests to external servers
    val server = varchar("server", maxServerNameLength)
    val icon = text("icon").nullable()
    val name = text("name")
    val mainGroup = text("main_group").nullable()
    val groups = json<Array<String>>("groups", jsonSerializer).default(emptyArray())
    val catchupDays = uinteger("catchup_days").nullable()
    val m3uProps = json<Map<String, String>>("m3u_props", jsonSerializer).default(emptyMap())
    val vlcOpts = json<Map<String, String>>("vlc_opts", jsonSerializer).default(emptyMap())
    val type = enumerationByName(name = "type", length = IptvChannelType.maxDbLength, klass = IptvChannelType::class)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex(
            customIndexName = "uniq_037df95408f7de3c48f8ff391f86c3df",
            server,
            xtreamStreamId,
            url,
        )
        index(
            customIndexName = "idx_81e0d67366a2ec3bed4ad79abd8f8940",
            isUnique = false,
            server,
            externalPosition,
        )
        index(
            customIndexName = "idx_82c78113ffde7ad2c641b8b94b4afa95",
            isUnique = false,
            server,
            mainGroup,
        )
    }
}

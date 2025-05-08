package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.Table

object EpgChannelDisplayNameTable : Table("epg_display_name") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val server = varchar("server", maxServerNameLength)
    val language = varchar("language", defaultVarcharLength)
    val name = text("name")

    override val primaryKey = PrimaryKey(arrayOf(server, epgChannelId, language))

    init {
        index(
            customIndexName = "idx_683b6e0bd327f4f67b64152de4231de1",
            columns = arrayOf(server, epgChannelId),
        )
    }
}

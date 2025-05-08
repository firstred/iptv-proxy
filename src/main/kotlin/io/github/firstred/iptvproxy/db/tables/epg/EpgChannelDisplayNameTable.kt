package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.Table

object EpgChannelDisplayNameTable : Table("epg_display_name") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val language = varchar("language", defaultVarcharLength)
    val name = text("name")

    override val primaryKey = PrimaryKey(arrayOf(epgChannelId, language))
}

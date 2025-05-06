package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.dtos.config.defaultVarcharLength
import io.github.firstred.iptvproxy.dtos.config.maxServerNameDbLength
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object EpgChannelDisplayNameTable : Table("epg_display_name") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val server = varchar("server", maxServerNameDbLength)
    val language = varchar("language", defaultVarcharLength)
    val name = text("name")

    override val primaryKey = PrimaryKey(arrayOf(epgChannelId, language, server))

    init {
        index(
            customIndexName = "idx_683b6e0bd327f4f67b64152de4231de1",
            columns = arrayOf(epgChannelId, server),
        )

        foreignKey(
            epgChannelId to EpgChannelTable.epgChannelId,
            server to EpgChannelTable.server,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_171b90b68e926fefee70c917964a5a24",
        )
    }
}

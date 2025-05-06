package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.dtos.config.defaultVarcharLength
import io.github.firstred.iptvproxy.dtos.config.maxServerNameDbLength
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgChannelTable : Table("epg_channel") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val server = varchar("server", maxServerNameDbLength)
    val icon = text("icon").nullable()
    val name = varchar("name", 511)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(arrayOf(server, epgChannelId))

    init {
        index(
            customIndexName = "idx_2bffdfa0f6c4be29485c21a215348ff5",
            isUnique = false,
            server,
            epgChannelId,
        )
    }
}

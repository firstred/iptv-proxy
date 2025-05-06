package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammeTable : Table("epg_programme") {
    val server = varchar("server", maxServerNameLength)
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val start = timestamp("start")
    val stop = timestamp("stop")
    val title = text("title")
    val subtitle = text("subtitle")
    val description = text("description")
    val icon = text("icon").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(arrayOf(server, epgChannelId, start))

    init {
        foreignKey(
            epgChannelId to EpgChannelTable.epgChannelId,
            server to EpgChannelTable.server,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_89f69f1c74e354168740cbf40d6c44cf",
        )
    }
}

package io.github.firstred.iptvproxy.db.tables.epg

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EpgProgrammeTable : LongIdTable("epg_programme") {
    val epgChannelId: Column<String> = varchar("epg_channel_id", 255)
    val server: Column<String> = varchar("server", 255)
    val title: Column<String> = text("title")
    val subtitle: Column<String> = text("subtitle")
    val description: Column<String> = text("description")
    val start: Column<Instant> = timestamp("start")
    val stop: Column<Instant> = timestamp("stop")
    val icon: Column<String?> = text("icon").nullable()
    val createdAt: Column<Instant> = timestamp("created_at").default(Clock.System.now())
    val updatedAt: Column<Instant> = timestamp("updated_at").default(Clock.System.now())

    init {
        foreignKey(
            epgChannelId to EpgChannelTable.epgChannelId,
            server to EpgChannelTable.server,
            onUpdate = ReferenceOption.CASCADE,
            onDelete = ReferenceOption.CASCADE,
            name = "fk_8jsdfh8sdf8h8sdfh8sdf",
        )

        uniqueIndex(
            customIndexName = "unq_ftytsdf8sdfh8sdfh8sdf",
            epgChannelId,
            server,
            start,
        )
    }
}

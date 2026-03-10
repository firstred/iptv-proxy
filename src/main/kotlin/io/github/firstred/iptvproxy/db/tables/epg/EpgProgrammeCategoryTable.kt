package io.github.firstred.iptvproxy.db.tables.epg

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object EpgProgrammeCategoryTable : Table("epg_programme_category") {
    val epgChannelId = varchar("epg_channel_id", defaultVarcharLength)
    val programmeStart: Column<Instant> = timestamp("programme_start")
    val language = varchar("language", defaultVarcharLength)
    val category = text("category")

    override val primaryKey = PrimaryKey(arrayOf(epgChannelId, programmeStart, language))
}

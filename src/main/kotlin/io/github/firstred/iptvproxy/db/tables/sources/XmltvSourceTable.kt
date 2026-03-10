package io.github.firstred.iptvproxy.db.tables.sources

import io.github.firstred.iptvproxy.utils.maxServerNameLength
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object XmltvSourceTable : Table("xmltv_source") {
    val server = varchar("server", maxServerNameLength)
    val generatorInfoName = text("generator_info_name").nullable()
    val generatorInfoUrl = text("generator_info_url").nullable()
    val sourceInfoUrl = text("source_info_url").nullable()
    val sourceInfoName = text("source_info_name").nullable()
    val sourceInfoLogo = text("source_info_logo").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val startedImportAt = timestamp("started_import_at").default(Clock.System.now())
    val completedImportAt = timestamp("completed_import_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(server) // Only one source per server
}

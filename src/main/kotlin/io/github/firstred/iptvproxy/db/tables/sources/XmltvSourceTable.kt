package io.github.firstred.iptvproxy.db.tables.sources

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object XmltvSourceTable : Table("xmltv_source") {
    val server: Column<String> = varchar("server", 511)
    val generatorInfoName: Column<String?> = text("generator_info_name").nullable()
    val generatorInfoUrl: Column<String?> = text("generator_info_url").nullable()
    val sourceInfoUrl: Column<String?> = text("source_info_url").nullable()
    val sourceInfoName: Column<String?> = text("source_info_name").nullable()
    val sourceInfoLogo: Column<String?> = text("source_info_logo").nullable()
    val createdAt: Column<Instant> = timestamp("created_at").default(Clock.System.now())
    val updatedAt: Column<Instant> = timestamp("updated_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(arrayOf(server)) // Only one source per server
}

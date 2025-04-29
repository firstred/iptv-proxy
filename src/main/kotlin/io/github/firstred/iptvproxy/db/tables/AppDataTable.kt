package io.github.firstred.iptvproxy.db.tables

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object AppDataTable : Table("app_data") {
    val name: Column<String> = varchar("name", 511)
    val value: Column<String> = text("value")

    override val primaryKey = PrimaryKey(name)
}

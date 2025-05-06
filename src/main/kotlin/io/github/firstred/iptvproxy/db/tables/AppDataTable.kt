package io.github.firstred.iptvproxy.db.tables

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import org.jetbrains.exposed.sql.Table

object AppDataTable : Table("app_data") {
    val name = varchar("name", defaultVarcharLength)
    val value = text("value")

    override val primaryKey = PrimaryKey(name)
}

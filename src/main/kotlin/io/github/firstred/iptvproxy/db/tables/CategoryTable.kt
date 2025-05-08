package io.github.firstred.iptvproxy.db.tables

import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.dao.id.UIntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object CategoryTable : UIntIdTable("category") {
    val server = varchar("server", maxServerNameLength)
    val externalCategoryId = uinteger("external_category_id")
    val name = text("category_name")
    val parentId = uinteger("parent_id").default(0u)
    val type = enumerationByName("type", IptvChannelType.Companion.maxDbLength, IptvChannelType::class)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex(
            customIndexName = "uniq_438f002bba40a04a4e02faef0137476d",
            server,
            externalCategoryId,
        )
        index(
            customIndexName = "idx_892d7f30bf0f5e3b716cc5ed3ebdd10e",
            isUnique = false,
            server,
            name,
        )
    }
}

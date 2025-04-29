package io.github.firstred.iptvproxy.db.tables.channels

import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object SeriesTable : Table("series") {
    val num: Column<Int> = integer("num")
    val server: Column<String> = varchar("server", 511)
    val name: Column<String> = text("name")
    val seriesId: Column<String> = varchar("series_id", 511)
    val cover: Column<String> = varchar("cover", 511)
    val plot: Column<String> = varchar("plot", 511)
    val cast: Column<String> = varchar("cast", 511)
    val mainCategoryId: Column<String> = varchar("main_category_id", 511)
    val director: Column<String> = varchar("director", 511)
    val genre: Column<String> = varchar("genre", 511)
    val releaseDate: Column<String> = varchar("release_date", 511)
    val lastModified: Column<Instant> = timestamp("last_modified")
    val rating: Column<String> = varchar("rating", 511)
    val rating5Based: Column<String> = varchar("rating_5based", 511)

    override val primaryKey = PrimaryKey(arrayOf(server, num))
}

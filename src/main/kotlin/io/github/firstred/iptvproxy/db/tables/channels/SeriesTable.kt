package io.github.firstred.iptvproxy.db.tables.channels

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object SeriesTable : Table("series") {
    val num: Column<Long> = long("num")
    val server: Column<String> = varchar("server", 255)
    val name: Column<String> = text("name")
    val seriesId: Column<String> = varchar("series_id", 255)
    val cover: Column<String> = text("cover")
    val plot: Column<String> = text("plot")
    val cast: Column<String> = text("cast")
    val mainCategoryId: Column<Long?> = long("main_category_id").nullable()
    val director: Column<String> = text("director")
    val genre: Column<String> = text("genre")
    val releaseDate: Column<String> = varchar("release_date", 255)
    val lastModified: Column<String> = varchar("last_modified", 255)
    val rating: Column<String> = text("rating")
    val rating5Based: Column<String> = text("rating_5based")
    val backdropPath: Column<String?> = text("backdrop_path").nullable()
    val youtubeTrailer: Column<String?> = varchar("youtube_trailer", 255).nullable()
    val tmdb: Column<String?> = varchar("tmdb", 255).nullable()
    val episodeRunTime: Column<String?> = varchar("episode_run_time", 255).nullable()
    val createdAt: Column<Instant> = timestamp("created_at").default(Clock.System.now())
    val updatedAt: Column<Instant> = timestamp("updated_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(arrayOf(server, num))
}

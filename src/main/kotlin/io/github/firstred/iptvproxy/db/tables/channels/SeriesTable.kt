package io.github.firstred.iptvproxy.db.tables.channels

import io.github.firstred.iptvproxy.utils.defaultVarcharLength
import io.github.firstred.iptvproxy.utils.maxServerNameLength
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object SeriesTable : Table("series") {
    val num = uinteger("num").default(0u)
    val server = varchar("server", maxServerNameLength)
    val name = text("name")
    val externalSeriesId = uinteger("series_id")
    val cover = text("cover")
    val plot = text("plot")
    val cast = text("cast")
    val mainCategoryId = uinteger("main_category_id").nullable()
    val director = text("director")
    val genre = text("genre")
    val releaseDate = varchar("release_date", 255)
    val lastModified = varchar("last_modified", 255)
    val rating = text("rating")
    val rating5Based = float("rating_5based")
    val backdropPath = text("backdrop_path").nullable()
    val youtubeTrailer = varchar("youtube_trailer", defaultVarcharLength).nullable()
    val tmdb = uinteger("tmdb").nullable()
    val episodeRunTime = varchar("episode_run_time", defaultVarcharLength).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(arrayOf(server, externalSeriesId))

    init {
        index(
            customIndexName = "idx_01232b0855d37101b5903869e9b86d7d",
            isUnique = false,
            server,
            num,
        )
    }
}

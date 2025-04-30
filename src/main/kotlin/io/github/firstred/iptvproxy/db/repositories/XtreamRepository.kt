package io.github.firstred.iptvproxy.db.repositories

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.tables.channels.LiveStreamCategoryTable
import io.github.firstred.iptvproxy.db.tables.channels.LiveStreamTable
import io.github.firstred.iptvproxy.db.tables.channels.LiveStreamToCategoryTable
import io.github.firstred.iptvproxy.db.tables.channels.MovieCategoryTable
import io.github.firstred.iptvproxy.db.tables.channels.MovieTable
import io.github.firstred.iptvproxy.db.tables.channels.MovieToCategoryTable
import io.github.firstred.iptvproxy.db.tables.channels.SeriesCategoryTable
import io.github.firstred.iptvproxy.db.tables.channels.SeriesTable
import io.github.firstred.iptvproxy.db.tables.channels.SeriesToCategoryTable
import io.github.firstred.iptvproxy.db.tables.sources.XmltvSourceTable
import io.github.firstred.iptvproxy.db.tables.sources.XtreamSourceTable
import io.github.firstred.iptvproxy.dtos.xtream.XtreamCategoryIdServer
import io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStream
import io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStreamCategory
import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovie
import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovieCategory
import io.github.firstred.iptvproxy.dtos.xtream.XtreamSeries
import io.github.firstred.iptvproxy.dtos.xtream.XtreamSeriesCategory
import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.plugins.withForeignKeyChecksDisabled
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.unionAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class XtreamRepository : KoinComponent {
    private val channelRepository: ChannelRepository by inject()

    fun signalXtreamStartedForServer(server: String) {
        transaction {
            // Upsert the XMLTV source
            XmltvSourceTable.upsert {
                it[XmltvSourceTable.server] = server
                it[XmltvSourceTable.startedAt] = Clock.System.now()
            }
        }
    }
    fun signalXtreamCompletedForServer(server: String) {
        transaction {
            // Upsert the XMLTV source
            XmltvSourceTable.upsert {
                it[XmltvSourceTable.server] = server
                it[XmltvSourceTable.completedAt] = Clock.System.now()
            }
        }
    }

    private fun upsertLiveStreamCategories(liveStreamCategories: List<XtreamLiveStreamCategory>, server: String) {
        liveStreamCategories.chunked(config.database.chunkSize).forEach { chunk -> chunk.forEach { liveStreamCategory ->
            transaction { withForeignKeyChecksDisabled {
                LiveStreamCategoryTable.insertIgnoreAndGetId {
                    it[LiveStreamCategoryTable.server] = server
                    it[LiveStreamCategoryTable.externalCategoryId] = liveStreamCategory.id.toLong()
                    it[LiveStreamCategoryTable.name] = liveStreamCategory.name
                    it[LiveStreamCategoryTable.parentId] = liveStreamCategory.parentId
                }?.value?.let {
                    LiveStreamCategoryTable.update({ LiveStreamCategoryTable.id eq it }) {
                        it[LiveStreamCategoryTable.updatedAt] = Clock.System.now()
                    }
                }
            } }
        } }
    }
    private fun upsertLiveStreams(liveStreams: List<XtreamLiveStream>, server: String) {
        transaction { withForeignKeyChecksDisabled {
            val internalCategoryIds = getAllLiveStreamCategoryIds(server).values.associateBy { it.externalId }

            liveStreams.chunked(config.database.chunkSize).forEach { chunk ->
                // Find internal category IDs by external category IDs
                LiveStreamTable.batchUpsert(chunk) { liveStream ->
                    this[LiveStreamTable.server] = server
                    this[LiveStreamTable.name] = liveStream.name
                    this[LiveStreamTable.num] = liveStream.num.toLong()
                    this[LiveStreamTable.directSource] = liveStream.directSource
                    this[LiveStreamTable.epgChannelId] = liveStream.epgChannelId
                    this[LiveStreamTable.externalStreamId] = liveStream.streamId.toString()
                    this[LiveStreamTable.icon] = liveStream.streamIcon
                    this[LiveStreamTable.added] = liveStream.added
                    this[LiveStreamTable.isAdult] = liveStream.isAdult
                    this[LiveStreamTable.mainCategoryId] = liveStream.categoryId?.toLong()?.let { internalCategoryIds[it]?.id } // Translate external category ID to internal category ID
                    this[LiveStreamTable.customSid] = liveStream.customSid
                    this[LiveStreamTable.tvArchive] = liveStream.tvArchive
                    this[LiveStreamTable.tvArchiveDuration] = liveStream.tvArchiveDuration
                    this[LiveStreamTable.updatedAt] = Clock.System.now()
                }

                chunk.forEach { liveStream ->
                    LiveStreamToCategoryTable.batchUpsert(liveStream.categoryIds?.filterNotNull()?.map { it.toLong() }?.filter { null != internalCategoryIds[it]?.id } ?: emptyList()) { categoryId ->
                        this[LiveStreamToCategoryTable.server] = server
                        this[LiveStreamToCategoryTable.num] = liveStream.num.toLong()
                        this[LiveStreamToCategoryTable.categoryId] = internalCategoryIds[categoryId]!!.id // Translate external category ID to internal category ID
                    }
                }
            }
        } }
    }
    fun upsertLiveStreamsAndCategories(liveStreams: List<XtreamLiveStream>, liveStreamCategories: List<XtreamLiveStreamCategory>, server: String) {
        upsertLiveStreamCategories(liveStreamCategories, server)
        upsertLiveStreams(liveStreams, server)
    }

    private fun upsertMovieCategories(movieCategories: List<XtreamMovieCategory>, server: String) {
        movieCategories.chunked(config.database.chunkSize).forEach { chunk -> chunk.forEach { movieCategory ->
            transaction { withForeignKeyChecksDisabled {
                MovieCategoryTable.insertIgnoreAndGetId { it
                    it[MovieCategoryTable.server] = server
                    it[MovieCategoryTable.externalCategoryId] = movieCategory.id.toLong()
                    it[MovieCategoryTable.name] = movieCategory.name
                    it[MovieCategoryTable.parentId] = movieCategory.parentId
                    it[MovieCategoryTable.updatedAt] = Clock.System.now()
                }?.value?.let {
                    MovieCategoryTable.update({ MovieCategoryTable.id eq it }) {
                        it[MovieCategoryTable.updatedAt] = Clock.System.now()
                    }
                }
            } }
        } }
    }
    private fun upsertMovies(movies: List<XtreamMovie>, server: String) {
        transaction { withForeignKeyChecksDisabled {
            val internalCategoryIds = getAllMovieCategoryIds(server).values.associateBy { it.externalId }

            movies.chunked(config.database.chunkSize).forEach { chunk ->
                MovieTable.batchUpsert(chunk) { movie ->
                    this[MovieTable.server] = server
                    this[MovieTable.name] = movie.name
                    this[MovieTable.num] = movie.num.toLong()
                    this[MovieTable.directSource] = movie.directSource
                    this[MovieTable.externalStreamId] = movie.streamId.toString()
                    this[MovieTable.externalStreamIcon] = movie.streamIcon
                    this[MovieTable.trailer] = movie.trailer
                    this[MovieTable.rating] = movie.rating
                    this[MovieTable.rating5Based] = movie.rating5Based
                    this[MovieTable.tmdb] = movie.tmdb
                    this[MovieTable.added] = movie.added
                    this[MovieTable.isAdult] = movie.isAdult
                    this[MovieTable.mainCategoryId] = movie.categoryId?.toLong()?.let { internalCategoryIds[it]?.id } // Translate external category ID to internal category ID
                    this[MovieTable.containerExtension] = movie.containerExtension
                    this[MovieTable.customSid] = movie.customSid
                    this[MovieTable.updatedAt] = Clock.System.now()
                }

                chunk.forEach { movie ->
                    MovieToCategoryTable.batchUpsert(movie.categoryIds?.filterNotNull()?.map { it.toLong() }?.filter { null != internalCategoryIds[it]?.id } ?: emptyList()) { categoryId ->
                        this[MovieToCategoryTable.server] = server
                        this[MovieToCategoryTable.num] = movie.num.toLong()
                        this[MovieToCategoryTable.categoryId] = internalCategoryIds[categoryId]!!.id // Translate external category ID to internal category ID
                    }
                }
            }
        } }
    }
    fun upsertMoviesAndCategories(movies: List<XtreamMovie>, movieCategories: List<XtreamMovieCategory>, server: String) {
        upsertMovieCategories(movieCategories, server)
        upsertMovies(movies, server)
    }

    private fun upsertSeriesCategories(seriesCategories: List<XtreamSeriesCategory>, server: String) {
        seriesCategories.chunked(config.database.chunkSize).forEach { chunk -> chunk.forEach { seriesCategory ->
            transaction { withForeignKeyChecksDisabled {
                SeriesCategoryTable.insertIgnoreAndGetId { it
                    it[SeriesCategoryTable.server] = server
                    it[SeriesCategoryTable.externalCategoryId] = seriesCategory.id.toLong()
                    it[SeriesCategoryTable.name] = seriesCategory.name
                    it[SeriesCategoryTable.parentId] = seriesCategory.parentId
                }?.value?.let {
                    SeriesCategoryTable.update({ SeriesCategoryTable.id eq it }) {
                        it[SeriesCategoryTable.updatedAt] = Clock.System.now()
                    }
                }
            } }
        } }
    }
    private fun upsertSeries(series: List<XtreamSeries>, server: String) {
        transaction { withForeignKeyChecksDisabled {
            val internalCategoryIds = getAllSeriesCategoryIds(server).values.associateBy { it.externalId }

            series.chunked(config.database.chunkSize).forEach { chunk ->
                SeriesTable.batchUpsert(chunk) { serie ->
                    this[SeriesTable.server] = server
                    this[SeriesTable.name] = serie.name
                    this[SeriesTable.num] = serie.num.toLong()
                    this[SeriesTable.mainCategoryId] = serie.categoryId?.toLong()?.let { internalCategoryIds[it]?.id } // Translate external category ID to internal category ID
                    this[SeriesTable.seriesId] = serie.seriesId.toString()
                    this[SeriesTable.cover] = serie.cover
                    this[SeriesTable.plot] = serie.plot
                    this[SeriesTable.cast] = serie.cast
                    this[SeriesTable.director] = serie.director
                    this[SeriesTable.genre] = serie.genre
                    this[SeriesTable.releaseDate] = serie.releaseDate
                    this[SeriesTable.lastModified] = serie.lastModified
                    this[SeriesTable.rating] = serie.rating
                    this[SeriesTable.rating5Based] = serie.rating5Based
                    this[SeriesTable.backdropPath] = serie.backdropPath?.filterNotNull()?.joinToString(",")
                    this[SeriesTable.youtubeTrailer] = serie.youtubeTrailer
                    this[SeriesTable.tmdb] = serie.tmdb
                    this[SeriesTable.episodeRunTime] = serie.episodeRunTime
                    this[SeriesTable.updatedAt] = Clock.System.now()
                }

                chunk.forEach { series ->
                    SeriesToCategoryTable.batchUpsert(series.categoryIds?.filterNotNull()?.map { it.toLong() }?.filter { null != internalCategoryIds[it]?.id } ?: emptyList()) { categoryId ->
                        this[SeriesToCategoryTable.server] = server
                        this[SeriesToCategoryTable.num] = series.num.toLong()
                        this[SeriesToCategoryTable.categoryId] = internalCategoryIds[categoryId]!!.id // Translate external category ID to internal category ID
                    }
                }
            }
        } }
    }
    fun upsertSeriesAndCategories( series: List<XtreamSeries>, seriesCategories: List<XtreamSeriesCategory>, server: String) {
        upsertSeriesCategories(seriesCategories, server)
        upsertSeries(series, server)
    }

    fun matchXtreamWithChannels() {
        for (server in config.servers.map { it.name }) {
            channelRepository.forEachIptvChannelChunk { chunk ->
                // Look for all live stream ids that are not yet matched
                val externalStreamIds = chunk.mapNotNull { it.externalStreamId }

                val missingLiveStreams = transaction {
                    LiveStreamTable.selectAll()
                        .where { LiveStreamTable.server eq server }
                        .andWhere { LiveStreamTable.externalStreamId inList externalStreamIds }
                        .andWhere { LiveStreamTable.streamId.isNull() }
                }

                transaction { withForeignKeyChecksDisabled {
                    missingLiveStreams.forEach { item ->
                        chunk.find { it.externalStreamId == item[LiveStreamTable.externalStreamId] }?.let { channel ->
                            LiveStreamTable.update({ (LiveStreamTable.server eq server).and { LiveStreamTable.externalStreamId eq channel.externalStreamId!! } }) {
                                with(SqlExpressionBuilder) {
                                    it[LiveStreamTable.streamId] = channel.id?.toLong()
                                }
                            }
                        }
                    }
                } }

                val missingMovies = transaction {
                    MovieTable.selectAll()
                        .where { MovieTable.server eq server }
                        .andWhere { MovieTable.externalStreamId inList externalStreamIds }
                        .andWhere { MovieTable.streamId.isNull() }
                }

                transaction { withForeignKeyChecksDisabled {
                    missingMovies.forEach { item ->
                        chunk.find { it.externalStreamId == item[MovieTable.externalStreamId] }?.let { channel ->
                            MovieTable.update({ (MovieTable.server eq server).and { MovieTable.externalStreamId eq channel.externalStreamId!! } }) {
                                with(SqlExpressionBuilder) {
                                    it[MovieTable.streamId] = channel.id?.toLong()
                                }
                            }
                        }
                    }
                } }
            }
        }
    }

    fun getAllCategoryIds(server: String? = null): Map<Long, XtreamCategoryIdServer> {
        return transaction {
            val channelTypeLiteral = stringLiteral(IptvChannelType.live.type)

            val liveStreamCategoryQuery = LiveStreamCategoryTable
                .select(
                    listOf(
                        LiveStreamCategoryTable.id,
                        LiveStreamCategoryTable.externalCategoryId,
                        LiveStreamCategoryTable.server,
                        channelTypeLiteral,
                    )
                )
            server?.let { liveStreamCategoryQuery.where { LiveStreamCategoryTable.server eq it } }

            val movieCategoryQuery = MovieCategoryTable
                .select(
                    listOf(
                        MovieCategoryTable.id,
                        MovieCategoryTable.externalCategoryId,
                        MovieCategoryTable.server,
                        stringLiteral(IptvChannelType.movie.type),
                    )
                )
            server?.let { movieCategoryQuery.where { MovieCategoryTable.server eq it } }

            val seriesCategoryQuery = SeriesCategoryTable
                .select(
                    listOf(
                        SeriesCategoryTable.id,
                        SeriesCategoryTable.externalCategoryId,
                        SeriesCategoryTable.server,
                        stringLiteral(IptvChannelType.series.type),
                    )
                )
            server?.let { seriesCategoryQuery.where { SeriesCategoryTable.server eq it } }

            liveStreamCategoryQuery
                .unionAll(movieCategoryQuery)
                .unionAll(seriesCategoryQuery)
                .associateBy(
                    { it[LiveStreamCategoryTable.id].value },
                    { XtreamCategoryIdServer(
                        id = it[LiveStreamCategoryTable.externalCategoryId],
                        externalId = it[LiveStreamCategoryTable.id].value,
                        server = it[LiveStreamCategoryTable.server],
                        type = IptvChannelType.valueOf(it[channelTypeLiteral])
                    ) }
                )
        }
    }
    fun getAllLiveStreamCategoryIds(server: String? = null): Map<Long, XtreamCategoryIdServer> {
        return transaction {
            val query = LiveStreamCategoryTable
                .select(
                    listOf(
                        LiveStreamCategoryTable.id,
                        LiveStreamCategoryTable.externalCategoryId,
                        LiveStreamCategoryTable.server,
                    )
                )
            server?.let { query.where { LiveStreamCategoryTable.server eq it } }

            query.associateBy(
                { it[LiveStreamCategoryTable.id].value },
                { XtreamCategoryIdServer(
                    id = it[LiveStreamCategoryTable.id].value,
                    externalId = it[LiveStreamCategoryTable.externalCategoryId],
                    server = it[LiveStreamCategoryTable.server],
                    type = IptvChannelType.live,
                ) })
        }
    }
    fun getAllMovieCategoryIds(server: String? = null): Map<Long, XtreamCategoryIdServer> {
        return transaction {
            val query = MovieCategoryTable
                .select(
                    listOf(
                        MovieCategoryTable.id,
                        MovieCategoryTable.externalCategoryId,
                        MovieCategoryTable.server,
                    )
                )
            server?.let { query.where { MovieCategoryTable.server eq it } }

            query.associateBy(
                { it[MovieCategoryTable.id].value },
                { XtreamCategoryIdServer(
                    id = it[MovieCategoryTable.id].value,
                    externalId = it[MovieCategoryTable.externalCategoryId],
                    server = it[MovieCategoryTable.server],
                    type = IptvChannelType.movie,
                ) })
        }
    }
    fun getAllSeriesCategoryIds(server: String? = null): Map<Long, XtreamCategoryIdServer> {
        return transaction {
            val query = SeriesCategoryTable
                .select(
                    listOf(
                        SeriesCategoryTable.id,
                        SeriesCategoryTable.externalCategoryId,
                        SeriesCategoryTable.server,
                    )
                )
            server?.let { query.where { SeriesCategoryTable.server eq it } }

            query.associateBy(
                { it[SeriesCategoryTable.id].value },
                {
                    XtreamCategoryIdServer(
                        id = it[SeriesCategoryTable.id].value,
                        externalId = it[SeriesCategoryTable.externalCategoryId],
                        server = it[SeriesCategoryTable.server],
                        type = IptvChannelType.series,
                    )
                })
        }
    }

    fun forEachLiveStreamChunk(
        server: String? = null,
        sortedByName: Boolean = false,
        chunkSize: Int = config.database.chunkSize,
        action: (List<XtreamLiveStream>) -> Unit,
    ) {
        var offset = 0L

        do {
            val categoryIds = CustomFunction(
                "GROUP_CONCAT",
                TextColumnType(),
                LiveStreamToCategoryTable.categoryId,
                stringLiteral(",")
            )

            val liveStreamQuery = LiveStreamTable.join(
                LiveStreamToCategoryTable,
                JoinType.INNER,
                additionalConstraint = { (LiveStreamTable.num eq LiveStreamToCategoryTable.num).and { LiveStreamTable.server eq LiveStreamToCategoryTable.server } }
            )
                .select(
                    LiveStreamTable.streamId,
                    LiveStreamTable.num,
                    LiveStreamTable.server,
                    LiveStreamTable.name,
                    LiveStreamTable.streamId,
                    LiveStreamTable.externalStreamId,
                    LiveStreamTable.icon,
                    LiveStreamTable.epgChannelId,
                    LiveStreamTable.added,
                    LiveStreamTable.isAdult,
                    LiveStreamTable.mainCategoryId,
                    LiveStreamTable.customSid,
                    LiveStreamTable.tvArchive,
                    LiveStreamTable.directSource,
                    LiveStreamTable.tvArchiveDuration,
                    categoryIds,
                )
                .groupBy(LiveStreamTable.streamId, LiveStreamTable.server)
            server?.let { liveStreamQuery.where { LiveStreamTable.server eq it } }
            if (sortedByName) liveStreamQuery.orderBy(LiveStreamTable.name)
            liveStreamQuery
                .limit(chunkSize)
                .offset(offset)
            val channels: List<XtreamLiveStream> = transaction {
                liveStreamQuery.map { it.toXtreamLiveStream(categoryIds) }
            }

            if (channels.isEmpty()) break

            action(channels)
            offset += chunkSize
        } while (channels.isNotEmpty())
    }

    fun cleanup() {
        transaction {
            LiveStreamTable.deleteWhere {
                LiveStreamTable.server notInList config.servers.map { it.name }
            }
            MovieTable.deleteWhere {
                MovieTable.server notInList config.servers.map { it.name }
            }
            SeriesTable.deleteWhere {
                SeriesTable.server notInList config.servers.map { it.name }
            }
            XtreamSourceTable.deleteWhere {
                XtreamSourceTable.server notInList config.servers.map { it.name }
            }

            for (server in config.servers.map { it.name }) {
                try {
                    val (startedAt, completedAt) = XtreamSourceTable
                        .select(listOf(XtreamSourceTable.startedAt, XtreamSourceTable.completedAt))
                        .where  { XtreamSourceTable.server eq server }
                        .map { Pair(it[XtreamSourceTable.startedAt], it[XtreamSourceTable.completedAt]) }
                        .first()
                    if (completedAt > startedAt) continue // Continue if the run hasn't finished (yet)

                    LiveStreamTable.deleteWhere {
                        LiveStreamTable.server eq server and
                                (LiveStreamTable.updatedAt less startedAt)
                    }
                    LiveStreamCategoryTable.deleteWhere {
                        LiveStreamCategoryTable.server eq server and
                                (LiveStreamCategoryTable.updatedAt less startedAt)
                    }
                    MovieTable.deleteWhere {
                        MovieTable.server eq server and
                                (MovieTable.updatedAt less startedAt)
                    }
                    MovieCategoryTable.deleteWhere {
                        MovieCategoryTable.server eq server and
                                (MovieCategoryTable.updatedAt less startedAt)
                    }
                    SeriesTable.deleteWhere {
                        SeriesTable.server eq server and
                                (SeriesTable.updatedAt less startedAt)
                    }
                    SeriesCategoryTable.deleteWhere {
                        SeriesCategoryTable.server eq server and
                                (SeriesCategoryTable.updatedAt less startedAt)
                    }
                } catch (_: NoSuchElementException) {
                }
            }
        }
    }

    companion object {
        fun ResultRow.toXtreamLiveStream(categoryIdsGroupConcat: CustomFunction<String>? = null) = XtreamLiveStream(
            num = this[LiveStreamTable.num].toInt(),
            name = this[LiveStreamTable.name],
            streamType = IptvChannelType.live,
            streamId = this[LiveStreamTable.streamId]?.toInt() ?: 0,
            streamIcon = this[LiveStreamTable.icon],
            epgChannelId = this[LiveStreamTable.epgChannelId],
            added = this[LiveStreamTable.added],
            isAdult = this[LiveStreamTable.isAdult],
            categoryId = this[LiveStreamToCategoryTable.categoryId].toString(),
            categoryIds = categoryIdsGroupConcat?.let { this[categoryIdsGroupConcat].split(",").toList() } ?: emptyList(),
        )
    }
}

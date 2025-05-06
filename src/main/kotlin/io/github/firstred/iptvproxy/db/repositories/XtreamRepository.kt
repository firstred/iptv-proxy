package io.github.firstred.iptvproxy.db.repositories

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.tables.ChannelTable
import io.github.firstred.iptvproxy.db.tables.channels.CategoryTable
import io.github.firstred.iptvproxy.db.tables.channels.LiveStreamTable
import io.github.firstred.iptvproxy.db.tables.channels.LiveStreamToCategoryTable
import io.github.firstred.iptvproxy.db.tables.channels.MovieTable
import io.github.firstred.iptvproxy.db.tables.channels.MovieToCategoryTable
import io.github.firstred.iptvproxy.db.tables.channels.SeriesTable
import io.github.firstred.iptvproxy.db.tables.channels.SeriesToCategoryTable
import io.github.firstred.iptvproxy.db.tables.sources.XmltvSourceTable
import io.github.firstred.iptvproxy.db.tables.sources.XtreamSourceTable
import io.github.firstred.iptvproxy.dtos.xtream.XtreamCategory
import io.github.firstred.iptvproxy.dtos.xtream.XtreamCategoryIdServer
import io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStream
import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovie
import io.github.firstred.iptvproxy.dtos.xtream.XtreamSeries
import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.utils.toBoolean
import io.github.firstred.iptvproxy.utils.toUInt
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.unionAll
import org.jetbrains.exposed.sql.upsert
import org.koin.core.component.KoinComponent

class XtreamRepository : KoinComponent {
    fun signalXtreamImportStartedForServer(server: String) {
        transaction {
            // Upsert the XMLTV source
            XmltvSourceTable.upsert {
                it[XmltvSourceTable.server] = server
                it[XmltvSourceTable.startedImportAt] = Clock.System.now()
            }
        }
    }
    fun signalXtreamImportCompletedForServer(server: String) {
        transaction {
            // Upsert the XMLTV source
            XmltvSourceTable.upsert {
                it[XmltvSourceTable.server] = server
                it[XmltvSourceTable.completedImportAt] = Clock.System.now()
            }
        }
    }

    private fun upsertCategories(liveStreamCategories: List<XtreamCategory>, server: String, type: IptvChannelType) {
        transaction {
            liveStreamCategories.chunked(config.database.chunkSize.toInt()).forEach { chunk ->
                CategoryTable.batchUpsert(
                    data = chunk,
                    keys = arrayOf(CategoryTable.server, CategoryTable.externalCategoryId),
                    shouldReturnGeneratedValues = false,
                ) { liveStreamCategory ->
                    this[CategoryTable.server] = server
                    this[CategoryTable.externalCategoryId] = liveStreamCategory.id.toUInt()
                    this[CategoryTable.name] = liveStreamCategory.name
                    this[CategoryTable.parentId] = liveStreamCategory.parentId.toUIntOrNull() ?: 0u
                    this[CategoryTable.type] = type
                    this[CategoryTable.updatedAt] = Clock.System.now()
                }
            }
        }
    }
    private fun upsertLiveStreams(liveStreams: List<XtreamLiveStream>, server: String) {
        transaction {
            val internalCategoryIds = getAllLiveStreamCategoryIds(server).values.associateBy { it.externalId }

            liveStreams.chunked(config.database.chunkSize.toInt()).forEach { chunk ->
                // Find internal category IDs by external category IDs
                LiveStreamTable.batchUpsert(
                    data = chunk,
                    shouldReturnGeneratedValues = false,
                ) { liveStream ->
                    this[LiveStreamTable.server] = server
                    this[LiveStreamTable.name] = liveStream.name
                    this[LiveStreamTable.num] = liveStream.num
                    this[LiveStreamTable.directSource] = liveStream.directSource
                    this[LiveStreamTable.epgChannelId] = liveStream.epgChannelId
                    this[LiveStreamTable.externalStreamId] = liveStream.streamId
                    this[LiveStreamTable.icon] = liveStream.streamIcon
                    this[LiveStreamTable.added] = Instant.fromEpochSeconds(liveStream.added.toLong())
                    this[LiveStreamTable.isAdult] = liveStream.isAdult.toBoolean()
                    this[LiveStreamTable.mainCategoryId] = liveStream.categoryId?.toUIntOrNull()?.let { internalCategoryIds[it]?.id } // Translate external category ID to internal category ID
                    this[LiveStreamTable.customSid] = liveStream.customSid
                    this[LiveStreamTable.tvArchive] = liveStream.tvArchive.toBoolean()
                    this[LiveStreamTable.tvArchiveDuration] = liveStream.tvArchiveDuration
                    this[LiveStreamTable.updatedAt] = Clock.System.now()
                }

                @Suppress("UNCHECKED_CAST")
                val liveStreamsAndCategoryIds = chunk
                    .flatMap { liveStream -> listOf(Triple(internalCategoryIds[liveStream.categoryId?.toUIntOrNull()]?.id, server, liveStream.streamId)) + (liveStream.categoryIds?.filterNotNull()?.map { Triple(internalCategoryIds[it]?.id, server, liveStream.streamId) } ?: emptyList()) }
                    .filter { null != it.first } as List<Triple<UInt, String, UInt>>

                LiveStreamToCategoryTable.deleteWhere {
                    LiveStreamToCategoryTable.server eq server and (LiveStreamToCategoryTable.externalStreamId inList liveStreams.map { it.streamId })
                }
                if (liveStreamsAndCategoryIds.isNotEmpty()) LiveStreamToCategoryTable.batchUpsert(
                    data = liveStreamsAndCategoryIds,
                    shouldReturnGeneratedValues = false,
                ) { (categoryId, server, streamId) ->
                    this[LiveStreamToCategoryTable.server] = server
                    this[LiveStreamToCategoryTable.externalStreamId] = streamId
                    this[LiveStreamToCategoryTable.categoryId] = categoryId // Translate external category ID to internal category ID
                }
            }
        }
    }
    fun upsertLiveStreamsAndCategories(liveStreams: List<XtreamLiveStream>, liveStreamCategories: List<XtreamCategory>, server: String) {
        upsertCategories(liveStreamCategories, server, type = IptvChannelType.live)
        upsertLiveStreams(liveStreams, server)
    }

    private fun upsertMovies(movies: List<XtreamMovie>, server: String) {
        transaction {
            val internalCategoryIds = getAllMovieCategoryIds(server).values.associateBy { it.externalId }

            movies.chunked(config.database.chunkSize.toInt()).forEach { chunk ->
                MovieTable.batchUpsert(
                    data = chunk,
                    shouldReturnGeneratedValues = false,
                ) { movie ->
                    this[MovieTable.server] = server
                    this[MovieTable.name] = movie.name
                    this[MovieTable.num] = movie.num
                    this[MovieTable.directSource] = movie.directSource
                    this[MovieTable.externalStreamId] = movie.streamId
                    this[MovieTable.externalStreamIcon] = movie.streamIcon
                    this[MovieTable.trailer] = movie.trailer
                    this[MovieTable.rating] = movie.rating
                    this[MovieTable.rating5Based] = movie.rating5Based
                    this[MovieTable.tmdb] = movie.tmdb.toUIntOrNull()
                    this[MovieTable.added] = Instant.fromEpochSeconds(movie.added.toLongOrNull() ?: 0L)
                    this[MovieTable.isAdult] = movie.isAdult.toBoolean()
                    this[MovieTable.mainCategoryId] = movie.categoryId?.toUIntOrNull()?.let { internalCategoryIds[it]?.id } // Translate external category ID to internal category ID
                    this[MovieTable.containerExtension] = movie.containerExtension
                    this[MovieTable.customSid] = movie.customSid
                    this[MovieTable.updatedAt] = Clock.System.now()
                }

                @Suppress("UNCHECKED_CAST") val moviesAndCategoryIds = movies
                    .flatMap { movie -> listOf(Triple(internalCategoryIds[movie.categoryId?.toUIntOrNull()]?.id, server, movie.streamId)) + (movie.categoryIds?.filterNotNull()?.map { Triple(internalCategoryIds[it]?.id, server, movie.streamId) } ?: emptyList()) }
                    .filter { null != it.first } as List<Triple<UInt, String, UInt>>

                MovieToCategoryTable.deleteWhere {
                    MovieToCategoryTable.server eq server and (MovieToCategoryTable.externalStreamId inList movies.map { it.streamId })
                }
                if (moviesAndCategoryIds.isNotEmpty()) MovieToCategoryTable.batchUpsert(
                    data = moviesAndCategoryIds,
                    shouldReturnGeneratedValues = false,
                ) { (categoryId, server, streamId) ->
                    this[MovieToCategoryTable.server] = server
                    this[MovieToCategoryTable.externalStreamId] = streamId
                    this[MovieToCategoryTable.categoryId] = categoryId
                }
            }
        }
    }
    fun upsertMoviesAndCategories(movies: List<XtreamMovie>, movieCategories: List<XtreamCategory>, server: String) {
        upsertCategories(movieCategories, server, type = IptvChannelType.movie)
        upsertMovies(movies, server)
    }

    private fun upsertSeries(series: List<XtreamSeries>, server: String) {
        transaction {
            val internalCategoryIds = getAllSeriesCategoryIds(server).values.associateBy { it.externalId }

            series.chunked(config.database.chunkSize.toInt()).forEach { chunk ->
                SeriesTable.batchUpsert(
                    data = chunk,
                    shouldReturnGeneratedValues = false,
                ) { serie ->
                    this[SeriesTable.server] = server
                    this[SeriesTable.name] = serie.name
                    this[SeriesTable.num] = serie.num
                    this[SeriesTable.mainCategoryId] = serie.categoryId?.toUIntOrNull()?.let { internalCategoryIds[it]?.id } // Translate external category ID to internal category ID
                    this[SeriesTable.externalSeriesId] = serie.seriesId
                    this[SeriesTable.cover] = serie.cover
                    this[SeriesTable.plot] = serie.plot
                    this[SeriesTable.cast] = serie.cast
                    this[SeriesTable.director] = serie.director
                    this[SeriesTable.genre] = serie.genre
                    this[SeriesTable.releaseDate] = serie.releaseDate
                    this[SeriesTable.lastModified] = serie.lastModified
                    this[SeriesTable.rating] = serie.rating
                    this[SeriesTable.rating5Based] = serie.rating5Based.toFloat()
                    this[SeriesTable.backdropPath] = serie.backdropPath?.filterNotNull()?.joinToString(",")
                    this[SeriesTable.youtubeTrailer] = serie.youtubeTrailer
                    this[SeriesTable.tmdb] = serie.tmdb.toUIntOrNull()
                    this[SeriesTable.episodeRunTime] = serie.episodeRunTime
                    this[SeriesTable.updatedAt] = Clock.System.now()
                }

                @Suppress("UNCHECKED_CAST")
                val seriesAndCategoryIds = series
                    .flatMap { series -> listOf(Triple(internalCategoryIds[series.categoryId?.toUIntOrNull()]?.id, server, series.seriesId)) + (series.categoryIds?.filterNotNull()?.map { Triple(internalCategoryIds[it]?.id, server, series.seriesId) } ?: emptyList()) }
                    .filter { null != it.first } as List<Triple<UInt, String, UInt>>

                SeriesToCategoryTable.deleteWhere {
                    SeriesToCategoryTable.server eq server and (SeriesToCategoryTable.externalSeriesId inList series.map { it.seriesId })
                }
                if (seriesAndCategoryIds.isNotEmpty()) SeriesToCategoryTable.batchUpsert(
                    data = seriesAndCategoryIds,
                    shouldReturnGeneratedValues = false,
                ) { (categoryId, server, seriesId) ->
                    this[SeriesToCategoryTable.server] = server
                    this[SeriesToCategoryTable.externalSeriesId] = seriesId
                    this[SeriesToCategoryTable.categoryId] = categoryId
                }
            }
        }
    }
    fun upsertSeriesAndCategories(series: List<XtreamSeries>, seriesCategories: List<XtreamCategory>, server: String) {
        upsertCategories(seriesCategories, server, type = IptvChannelType.series)
        upsertSeries(series, server)
    }

    fun getAllCategoryIds(server: String? = null): Map<UInt, XtreamCategoryIdServer> {
        return transaction {
            val channelTypeLiteral = stringLiteral(IptvChannelType.live.type)

            val liveStreamCategoryQuery = CategoryTable
                .select(
                    listOf(
                        CategoryTable.id,
                        CategoryTable.externalCategoryId,
                        CategoryTable.server,
                        channelTypeLiteral,
                    )
                )
            server?.let { liveStreamCategoryQuery.where { CategoryTable.server eq it } }

            val movieCategoryQuery = CategoryTable
                .select(
                    listOf(
                        CategoryTable.id,
                        CategoryTable.externalCategoryId,
                        CategoryTable.server,
                        stringLiteral(IptvChannelType.movie.type),
                    )
                )
            server?.let { movieCategoryQuery.where { CategoryTable.server eq it } }

            val seriesCategoryQuery = CategoryTable
                .select(
                    listOf(
                        CategoryTable.id,
                        CategoryTable.externalCategoryId,
                        CategoryTable.server,
                        stringLiteral(IptvChannelType.series.type),
                    )
                )
            server?.let { seriesCategoryQuery.where { CategoryTable.server eq it } }

            liveStreamCategoryQuery
                .unionAll(movieCategoryQuery)
                .unionAll(seriesCategoryQuery)
                .associateBy(
                    { it[CategoryTable.id].value },
                    { XtreamCategoryIdServer(
                        id = it[CategoryTable.externalCategoryId],
                        externalId = it[CategoryTable.id].value,
                        server = it[CategoryTable.server],
                        type = IptvChannelType.valueOf(it[channelTypeLiteral])
                    ) }
                )
        }
    }
    fun getAllLiveStreamCategoryIds(server: String? = null): Map<UInt, XtreamCategoryIdServer> {
        return transaction {
            val query = CategoryTable
                .select(
                    listOf(
                        CategoryTable.id,
                        CategoryTable.externalCategoryId,
                        CategoryTable.server,
                    )
                )
            server?.let { query.where { CategoryTable.server eq it } }

            query.associateBy(
                { it[CategoryTable.id].value },
                { XtreamCategoryIdServer(
                    id = it[CategoryTable.id].value,
                    externalId = it[CategoryTable.externalCategoryId],
                    server = it[CategoryTable.server],
                    type = IptvChannelType.live,
                ) })
        }
    }
    fun getAllMovieCategoryIds(server: String? = null): Map<UInt, XtreamCategoryIdServer> {
        return transaction {
            val query = CategoryTable
                .select(
                    listOf(
                        CategoryTable.id,
                        CategoryTable.externalCategoryId,
                        CategoryTable.server,
                    )
                )
            server?.let { query.where { CategoryTable.server eq it } }

            query.associateBy(
                { it[CategoryTable.id].value },
                { XtreamCategoryIdServer(
                    id = it[CategoryTable.id].value,
                    externalId = it[CategoryTable.externalCategoryId],
                    server = it[CategoryTable.server],
                    type = IptvChannelType.movie,
                ) })
        }
    }
    fun getAllSeriesCategoryIds(server: String? = null): Map<UInt, XtreamCategoryIdServer> {
        return transaction {
            val query = CategoryTable
                .select(
                    listOf(
                        CategoryTable.id,
                        CategoryTable.externalCategoryId,
                        CategoryTable.server,
                    )
                )
            server?.let { query.where { CategoryTable.server eq it } }

            query.associateBy(
                { it[CategoryTable.id].value },
                {
                    XtreamCategoryIdServer(
                        id = it[CategoryTable.id].value,
                        externalId = it[CategoryTable.externalCategoryId],
                        server = it[CategoryTable.server],
                        type = IptvChannelType.series,
                    )
                })
        }
    }

    fun forEachLiveStreamChunk(
        server: String? = null,
        sortedByName: Boolean = config.sortChannelsByName,
        categoryId: UInt? = null,
        chunkSize: Int = config.database.chunkSize.toInt(),
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

            val liveStreamQuery = LiveStreamTable
                .join(
                    LiveStreamToCategoryTable,
                    JoinType.LEFT,
                    onColumn = LiveStreamTable.externalStreamId,
                    otherColumn = LiveStreamToCategoryTable.externalStreamId,
                    additionalConstraint = { LiveStreamTable.server eq LiveStreamToCategoryTable.server },
                )
                .join(
                    ChannelTable,
                    JoinType.INNER,
                    onColumn = LiveStreamTable.externalStreamId,
                    otherColumn = ChannelTable.externalStreamId,
                    additionalConstraint = { ChannelTable.server eq ChannelTable.server },
                )
                .select(
                    LiveStreamTable.num,
                    LiveStreamTable.server,
                    LiveStreamTable.name,
                    LiveStreamTable.externalStreamId,
                    LiveStreamTable.icon,
                    LiveStreamTable.epgChannelId,
                    LiveStreamTable.server,
                    LiveStreamTable.added,
                    LiveStreamTable.isAdult,
                    LiveStreamTable.mainCategoryId,
                    LiveStreamTable.customSid,
                    LiveStreamTable.tvArchive,
                    LiveStreamTable.directSource,
                    LiveStreamTable.tvArchiveDuration,
                    ChannelTable.id,
                    ChannelTable.externalPosition,
                    categoryIds,
                )
                .groupBy(LiveStreamTable.externalStreamId, LiveStreamTable.server)
            server?.let { liveStreamQuery.where { LiveStreamTable.server eq it } }
            if (sortedByName) {
                liveStreamQuery.orderBy(LiveStreamTable.name to SortOrder.ASC)
            } else {
                liveStreamQuery.orderBy(LiveStreamTable.server to SortOrder.ASC, ChannelTable.externalPosition to SortOrder.ASC)
            }
            if (null != categoryId) liveStreamQuery.andWhere { LiveStreamToCategoryTable.categoryId eq categoryId }
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
    fun forEachCategoryChunk(
        type: IptvChannelType,
        server: String? = null,
        chunkSize: Int = config.database.chunkSize.toInt(),
        action: (List<XtreamCategory>) -> Unit,
    ) {
        var offset = 0L

        do {
            val liveStreamCategoryQuery = CategoryTable.selectAll()
                .where { CategoryTable.type eq type }
            server?.let { liveStreamCategoryQuery.where { CategoryTable.server eq it } }
            liveStreamCategoryQuery.orderBy(CategoryTable.id)
            liveStreamCategoryQuery
                .limit(chunkSize)
                .offset(offset)
            val categories: List<XtreamCategory> = transaction {
                liveStreamCategoryQuery.map { it.toXtreamCategory() }
            }

            if (categories.isEmpty()) break

            action(categories)
            offset += chunkSize
        } while (categories.isNotEmpty())
    }

    fun forEachMovieChunk(
        server: String? = null,
        sortedByName: Boolean = config.sortChannelsByName,
        categoryId: UInt? = null,
        chunkSize: Int = config.database.chunkSize.toInt(),
        action: (List<XtreamMovie>) -> Unit,
    ) {
        var offset = 0L

        do {
            val categoryIds = CustomFunction(
                "GROUP_CONCAT",
                TextColumnType(),
                MovieToCategoryTable.categoryId,
                stringLiteral(",")
            )

            val movieQuery = MovieTable
                .join(
                    MovieToCategoryTable,
                    JoinType.LEFT,
                    onColumn = MovieTable.externalStreamId,
                    otherColumn = MovieToCategoryTable.externalStreamId,
                    additionalConstraint = { MovieTable.server eq MovieToCategoryTable.server },
                )
                .join(
                    ChannelTable,
                    JoinType.INNER,
                    onColumn = MovieTable.externalStreamId,
                    otherColumn = ChannelTable.externalStreamId,
                    additionalConstraint = { MovieTable.server eq ChannelTable.server },
                )
                .select(
                    MovieTable.num,
                    MovieTable.server,
                    MovieTable.name,
                    MovieTable.externalStreamId,
                    MovieTable.externalStreamIcon,
                    MovieTable.server,
                    MovieTable.rating,
                    MovieTable.rating5Based,
                    MovieTable.tmdb,
                    MovieTable.trailer,
                    MovieTable.added,
                    MovieTable.isAdult,
                    MovieTable.mainCategoryId,
                    MovieTable.containerExtension,
                    MovieTable.customSid,
                    MovieTable.directSource,
                    ChannelTable.id,
                    ChannelTable.externalPosition,
                    ChannelTable.externalStreamId,
                    categoryIds,
                )
                .groupBy(MovieTable.externalStreamId, MovieTable.server)
            server?.let { movieQuery.where { MovieTable.server eq it } }
            if (sortedByName) {
                movieQuery.orderBy(MovieTable.name to SortOrder.ASC)
            } else {
                movieQuery.orderBy(MovieTable.server to SortOrder.ASC, ChannelTable.externalPosition to SortOrder.ASC)
            }
            if (null != categoryId) movieQuery.andWhere { MovieToCategoryTable.categoryId eq categoryId }
            movieQuery
                .limit(chunkSize)
                .offset(offset)
            val channels: List<XtreamMovie> = transaction {
                movieQuery.map { it.toXtreamMovie(categoryIds) }
            }

            if (channels.isEmpty()) break

            action(channels)
            offset += chunkSize
        } while (channels.isNotEmpty())
    }

    fun forEachSeriesChunk(
        server: String? = null,
        sortedByName: Boolean = false,
        categoryId: UInt? = null,
        chunkSize: Int = config.database.chunkSize.toInt(),
        action: (List<XtreamSeries>) -> Unit,
    ) {
        var offset = 0L

        do {
            val categoryIds = CustomFunction(
                "GROUP_CONCAT",
                TextColumnType(),
                SeriesToCategoryTable.categoryId,
                stringLiteral(",")
            )

            val seriesQuery = SeriesTable
                .join(
                    SeriesToCategoryTable,
                    JoinType.LEFT,
                    onColumn = SeriesTable.externalSeriesId,
                    otherColumn = SeriesToCategoryTable.externalSeriesId,
                    additionalConstraint = { SeriesTable.server eq SeriesToCategoryTable.server },
                )
                .select(
                    SeriesTable.num,
                    SeriesTable.server,
                    SeriesTable.name,
                    SeriesTable.externalSeriesId,
                    SeriesTable.server,
                    SeriesTable.cover,
                    SeriesTable.plot,
                    SeriesTable.cast,
                    SeriesTable.mainCategoryId,
                    SeriesTable.director,
                    SeriesTable.genre,
                    SeriesTable.releaseDate,
                    SeriesTable.lastModified,
                    SeriesTable.rating,
                    SeriesTable.rating5Based,
                    SeriesTable.backdropPath,
                    SeriesTable.youtubeTrailer,
                    SeriesTable.tmdb,
                    SeriesTable.episodeRunTime,
                    categoryIds,
                )
                .groupBy(SeriesTable.server, SeriesTable.externalSeriesId)
            server?.let { seriesQuery.where { SeriesTable.server eq it } }
            if (sortedByName) {
                seriesQuery.orderBy(SeriesTable.name to SortOrder.ASC)
            } else {
                seriesQuery.orderBy(SeriesTable.server to SortOrder.ASC, SeriesTable.externalSeriesId to SortOrder.ASC)
            }
            if (null != categoryId) seriesQuery.andWhere { SeriesToCategoryTable.categoryId eq categoryId }
            seriesQuery
                .limit(chunkSize)
                .offset(offset)
            val channels: List<XtreamSeries> = transaction {
                seriesQuery.map { it.toXtreamSeries(categoryIds) }
            }

            if (channels.isEmpty()) break

            action(channels)
            offset += chunkSize
        } while (channels.isNotEmpty())
    }

    fun findServerByLiveStreamId(liveStreamId: UInt): String? = transaction {
        ChannelTable
            .select(ChannelTable.server)
            .where { ChannelTable.id eq liveStreamId }
            .map { it[ChannelTable.server] }
            .firstOrNull()
    }
    fun findServerByMovieId(movieId: UInt): String? = findServerByLiveStreamId(movieId)
    fun findServerBySeriesId(seriesId: UInt): String? = transaction {
        SeriesTable
            .select(SeriesTable.server)
            .where { SeriesTable.externalSeriesId eq seriesId }
            .map { it[SeriesTable.server] }
            .firstOrNull()
    }

    fun getCategoryIdToExternalIdMap(): Map<UInt, UInt> {
        return transaction {
            CategoryTable
                .selectAll()
                .associateBy(
                    { it[CategoryTable.id].value },
                    { it[CategoryTable.externalCategoryId] },
                )
        }
    }
    fun getExternalCategoryIdToIdMap(): Map<UInt, UInt> {
        return transaction {
            CategoryTable
                .selectAll()
                .associateBy(
                    { it[CategoryTable.externalCategoryId] },
                    { it[CategoryTable.id].value },
                )
        }
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

            LiveStreamTable.deleteWhere {
                LiveStreamTable.updatedAt less (Clock.System.now() - config.channelMaxStalePeriod)
            }
            CategoryTable.deleteWhere {
                 CategoryTable.updatedAt less (Clock.System.now() - config.channelMaxStalePeriod)
            }
            MovieTable.deleteWhere {
                 MovieTable.updatedAt less (Clock.System.now() - config.channelMaxStalePeriod)
            }
            CategoryTable.deleteWhere {
                 CategoryTable.updatedAt less (Clock.System.now() - config.channelMaxStalePeriod)
            }
            SeriesTable.deleteWhere {
                 SeriesTable.updatedAt less (Clock.System.now() - config.channelMaxStalePeriod)
            }
            CategoryTable.deleteWhere {
                 CategoryTable.updatedAt less (Clock.System.now() - config.channelMaxStalePeriod)
            }
        }
    }

    companion object {
        fun ResultRow.toXtreamLiveStream(categoryIdsGroupConcat: CustomFunction<String>? = null) = XtreamLiveStream(
            num = this[LiveStreamTable.num],
            name = this[LiveStreamTable.name],
            streamType = IptvChannelType.live,
            typeName = IptvChannelType.live,
            streamId = this[ChannelTable.id].value,
            streamIcon = this[LiveStreamTable.icon],
            server = this[LiveStreamTable.server],
            epgChannelId = this[LiveStreamTable.epgChannelId],
            added = this[LiveStreamTable.added].epochSeconds.toString(),
            isAdult = this[LiveStreamTable.isAdult].toUInt(),
            categoryId = this[LiveStreamTable.mainCategoryId].toString(),
            categoryIds = ((categoryIdsGroupConcat?.let { this[categoryIdsGroupConcat]?.split(",")?.toList()?.map { it.toUInt() } } ?: emptyList()) + listOf(this[LiveStreamTable.mainCategoryId])).distinct(),
        )
        fun ResultRow.toXtreamMovie(categoryIdsGroupConcat: CustomFunction<String>? = null) = XtreamMovie(
            num = this[MovieTable.num],
            name = this[MovieTable.name],
            streamType = IptvChannelType.movie,
            typeName = IptvChannelType.movie,
            streamId = this[ChannelTable.id].value,
            server = this[MovieTable.server],
            added = this[MovieTable.added].epochSeconds.toString(),
            isAdult = this[MovieTable.isAdult].toUInt(),
            categoryId = this[MovieTable.mainCategoryId].toString(),
            categoryIds = ((categoryIdsGroupConcat?.let { this[categoryIdsGroupConcat]?.split(",")?.toList()?.map { it.toUInt() } } ?: emptyList()) + listOf(this[MovieTable.mainCategoryId])).distinct(),
            streamIcon = this[MovieTable.externalStreamIcon] ?: "",
            rating = this[MovieTable.rating] ?: "",
            rating5Based = this[MovieTable.rating5Based] ?: 0.0f,
            tmdb = this[MovieTable.tmdb]?.toString() ?: "",
            trailer = this[MovieTable.trailer] ?: "",
            customSid = this[MovieTable.customSid] ?: "",
            containerExtension = this[MovieTable.containerExtension],
            directSource = this[MovieTable.directSource] ?: "",
        )
        fun ResultRow.toXtreamSeries(categoryIdsGroupConcat: CustomFunction<String>? = null) = XtreamSeries(
            num = this[SeriesTable.num],
            name = this[SeriesTable.name],
            streamType = IptvChannelType.series,
            typeName = IptvChannelType.series,
            server = this[SeriesTable.server],
            categoryId = this[SeriesTable.mainCategoryId].toString(),
            categoryIds = ((categoryIdsGroupConcat?.let { this[categoryIdsGroupConcat]?.split(",")?.toList()?.map { it.toUInt() } } ?: emptyList()) + listOf(this[SeriesTable.mainCategoryId])).distinct(),
            rating = this[SeriesTable.rating],
            rating5Based = this[SeriesTable.rating5Based].toString(),
            tmdb = this[SeriesTable.tmdb]?.toString() ?: "",
            seriesId = this[SeriesTable.externalSeriesId],
            cover = this[SeriesTable.cover],
            plot = this[SeriesTable.plot],
            cast = this[SeriesTable.cast],
            director = this[SeriesTable.director],
            genre = this[SeriesTable.genre],
            releaseDate = this[SeriesTable.releaseDate],
            releaseDateUnderscore = this[SeriesTable.releaseDate],
            lastModified = this[SeriesTable.lastModified],
            backdropPath = this[SeriesTable.backdropPath]?.split(",")?.map { it.trim() } ?: emptyList(),
            youtubeTrailer = this[SeriesTable.youtubeTrailer] ?: "",
            episodeRunTime = this[SeriesTable.episodeRunTime] ?: "",
        )
        fun ResultRow.toXtreamCategory() = XtreamCategory(
            id = this[CategoryTable.id].toString(),
            name = this[CategoryTable.name],
            parentId = this[CategoryTable.parentId].toString(),
        )
    }
}

package io.github.firstred.iptvproxy.db.repositories

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.tables.IptvChannelTable
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
import io.github.firstred.iptvproxy.plugins.withForeignKeyConstraintsDisabled
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.unionAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import org.koin.core.component.KoinComponent

class XtreamRepository : KoinComponent {
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

    private fun upsertCategories(liveStreamCategories: List<XtreamCategory>, server: String, type: IptvChannelType) {
        liveStreamCategories.chunked(config.database.chunkSize).forEach { chunk -> chunk.forEach { liveStreamCategory ->
            transaction { withForeignKeyConstraintsDisabled {
                CategoryTable.insertIgnore {
                    it[CategoryTable.server] = server
                    it[CategoryTable.externalCategoryId] = liveStreamCategory.id.toLong()
                    it[CategoryTable.name] = liveStreamCategory.name
                    it[CategoryTable.parentId] = liveStreamCategory.parentId
                    it[CategoryTable.type] = type
                }

                CategoryTable.update({ (CategoryTable.server eq server) and (CategoryTable.externalCategoryId eq liveStreamCategory.id.toLong()) }) {
                    it[CategoryTable.updatedAt] = Clock.System.now()
                }
            } }
        } }
    }
    private fun upsertLiveStreams(liveStreams: List<XtreamLiveStream>, server: String) {
        transaction { withForeignKeyConstraintsDisabled {
            val internalCategoryIds = getAllLiveStreamCategoryIds(server).values.associateBy { it.externalId }

            liveStreams.chunked(config.database.chunkSize).forEach { chunk ->
                // Find internal category IDs by external category IDs
                LiveStreamTable.batchUpsert(chunk) { liveStream ->
                    this[LiveStreamTable.server] = server
                    this[LiveStreamTable.name] = liveStream.name
                    this[LiveStreamTable.num] = liveStream.num
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
                    LiveStreamToCategoryTable.batchUpsert(liveStream.categoryIds?.filterNotNull()?.map { it }?.filter { null != internalCategoryIds[it]?.id } ?: emptyList()) { categoryId ->
                        this[LiveStreamToCategoryTable.server] = server
                        this[LiveStreamToCategoryTable.num] = liveStream.num
                        this[LiveStreamToCategoryTable.categoryId] = internalCategoryIds[categoryId]!!.id // Translate external category ID to internal category ID
                    }
                }
            }
        } }
    }
    fun upsertLiveStreamsAndCategories(liveStreams: List<XtreamLiveStream>, liveStreamCategories: List<XtreamCategory>, server: String) {
        upsertCategories(liveStreamCategories, server, type = IptvChannelType.live)
        upsertLiveStreams(liveStreams, server)
    }

    private fun upsertMovies(movies: List<XtreamMovie>, server: String) {
        transaction { withForeignKeyConstraintsDisabled {
            val internalCategoryIds = getAllMovieCategoryIds(server).values.associateBy { it.externalId }

            movies.chunked(config.database.chunkSize).forEach { chunk ->
                MovieTable.batchUpsert(chunk) { movie ->
                    this[MovieTable.server] = server
                    this[MovieTable.name] = movie.name
                    this[MovieTable.num] = movie.num
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
                    MovieToCategoryTable.batchUpsert(movie.categoryIds?.filterNotNull()?.map { it }?.filter { null != internalCategoryIds[it]?.id } ?: emptyList()) { categoryId ->
                        this[MovieToCategoryTable.server] = server
                        this[MovieToCategoryTable.num] = movie.num
                        this[MovieToCategoryTable.categoryId] = internalCategoryIds[categoryId]!!.id // Translate external category ID to internal category ID
                    }
                }
            }
        } }
    }
    fun upsertMoviesAndCategories(movies: List<XtreamMovie>, movieCategories: List<XtreamCategory>, server: String) {
        upsertCategories(movieCategories, server, type = IptvChannelType.movie)
        upsertMovies(movies, server)
    }

    private fun upsertSeries(series: List<XtreamSeries>, server: String) {
        transaction { withForeignKeyConstraintsDisabled {
            val internalCategoryIds = getAllSeriesCategoryIds(server).values.associateBy { it.externalId }

            series.chunked(config.database.chunkSize).forEach { chunk ->
                SeriesTable.batchUpsert(chunk) { serie ->
                    this[SeriesTable.server] = server
                    this[SeriesTable.name] = serie.name
                    this[SeriesTable.num] = serie.num
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
                    SeriesToCategoryTable.batchUpsert(series.categoryIds?.filterNotNull()?.map { it }?.filter { null != internalCategoryIds[it]?.id } ?: emptyList()) { categoryId ->
                        this[SeriesToCategoryTable.server] = server
                        this[SeriesToCategoryTable.num] = series.num
                        this[SeriesToCategoryTable.categoryId] = internalCategoryIds[categoryId]!!.id // Translate external category ID to internal category ID
                    }
                }
            }
        } }
    }
    fun upsertSeriesAndCategories(series: List<XtreamSeries>, seriesCategories: List<XtreamCategory>, server: String) {
        upsertCategories(seriesCategories, server, type = IptvChannelType.series)
        upsertSeries(series, server)
    }

    fun getAllCategoryIds(server: String? = null): Map<Long, XtreamCategoryIdServer> {
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
    fun getAllLiveStreamCategoryIds(server: String? = null): Map<Long, XtreamCategoryIdServer> {
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
    fun getAllMovieCategoryIds(server: String? = null): Map<Long, XtreamCategoryIdServer> {
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
    fun getAllSeriesCategoryIds(server: String? = null): Map<Long, XtreamCategoryIdServer> {
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
        categoryId: Long? = null,
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

            val liveStreamQuery = LiveStreamTable
                .join(
                    LiveStreamToCategoryTable,
                    JoinType.INNER,
                    onColumn = LiveStreamTable.num,
                    otherColumn = LiveStreamToCategoryTable.num,
                    additionalConstraint = { LiveStreamTable.server eq LiveStreamToCategoryTable.server },
                )
                .join(
                    IptvChannelTable,
                    JoinType.INNER,
                    onColumn = LiveStreamTable.externalStreamId,
                    otherColumn = IptvChannelTable.externalStreamId,
                    additionalConstraint = { IptvChannelTable.server eq IptvChannelTable.server },
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
                    IptvChannelTable.id,
                    categoryIds,
                )
                .groupBy(LiveStreamTable.externalStreamId, LiveStreamTable.server)
            server?.let { liveStreamQuery.where { LiveStreamTable.server eq it } }
            if (sortedByName) {
                liveStreamQuery.orderBy(LiveStreamTable.name to SortOrder.ASC)
            } else {
                liveStreamQuery.orderBy(LiveStreamTable.server to SortOrder.ASC, LiveStreamTable.externalStreamId to SortOrder.ASC)
            }
            if (categoryId != null) liveStreamQuery.andWhere { LiveStreamToCategoryTable.categoryId eq categoryId }
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
        chunkSize: Int = config.database.chunkSize,
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
        categoryId: Long? = null,
        chunkSize: Int = config.database.chunkSize,
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
                    JoinType.INNER,
                    onColumn = MovieTable.num,
                    otherColumn = MovieToCategoryTable.num,
                    additionalConstraint = { MovieTable.server eq MovieToCategoryTable.server },
                )
                .join(
                    IptvChannelTable,
                    JoinType.INNER,
                    onColumn = MovieTable.externalStreamId,
                    otherColumn = IptvChannelTable.externalStreamId,
                    additionalConstraint = { MovieTable.server eq IptvChannelTable.server },
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
                    IptvChannelTable.id,
                    categoryIds,
                )
                .groupBy(MovieTable.externalStreamId, MovieTable.server)
            server?.let { movieQuery.where { MovieTable.server eq it } }
            if (sortedByName) {
                movieQuery.orderBy(MovieTable.name to SortOrder.ASC)
            } else {
                movieQuery.orderBy(MovieTable.server to SortOrder.ASC, MovieTable.externalStreamId to SortOrder.ASC)
            }
            if (categoryId != null) movieQuery.andWhere { MovieToCategoryTable.categoryId eq categoryId }
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
        categoryId: Long? = null,
        chunkSize: Int = config.database.chunkSize,
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

            val seriesQuery = SeriesTable.join(
                SeriesToCategoryTable,
                JoinType.INNER,
                additionalConstraint = { (SeriesTable.num eq SeriesToCategoryTable.num).and { SeriesTable.server eq SeriesToCategoryTable.server } }
            )
                .select(
                    SeriesTable.num,
                    SeriesTable.server,
                    SeriesTable.name,
                    SeriesTable.seriesId,
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
                .groupBy(SeriesTable.num, SeriesTable.server)
            server?.let { seriesQuery.where { SeriesTable.server eq it } }
            if (sortedByName) {
                seriesQuery.orderBy(SeriesTable.name to SortOrder.ASC)
            } else {
                seriesQuery.orderBy(SeriesTable.server to SortOrder.ASC, SeriesTable.seriesId to SortOrder.ASC)
            }
            if (categoryId != null) seriesQuery.andWhere { SeriesToCategoryTable.categoryId eq categoryId }
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

    fun findServerByLiveStreamId(liveStreamId: Long): String? = transaction {
        IptvChannelTable
            .select(IptvChannelTable.server)
            .where { IptvChannelTable.id eq liveStreamId }
            .map { it[IptvChannelTable.server] }
            .firstOrNull()
    }
    fun findServerByMovieId(movieId: Long): String? = findServerByLiveStreamId(movieId)
    fun findServerBySeriesId(seriesId: Long): String? = transaction {
        SeriesTable
            .select(SeriesTable.server)
            .where { SeriesTable.seriesId eq seriesId.toString() }
            .map { it[SeriesTable.server] }
            .firstOrNull()
    }

    fun getCategoryIdToExternalIdMap(): Map<Long, Long> {
        return transaction {
            CategoryTable
                .selectAll()
                .associateBy(
                    { it[CategoryTable.id].value },
                    { it[CategoryTable.externalCategoryId] },
                )
        }
    }
    fun getExternalCategoryIdToIdMap(): Map<Long, Long> {
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
                    CategoryTable.deleteWhere {
                        CategoryTable.server eq server and
                                (CategoryTable.updatedAt less startedAt)
                    }
                    MovieTable.deleteWhere {
                        MovieTable.server eq server and
                                (MovieTable.updatedAt less startedAt)
                    }
                    CategoryTable.deleteWhere {
                        CategoryTable.server eq server and
                                (CategoryTable.updatedAt less startedAt)
                    }
                    SeriesTable.deleteWhere {
                        SeriesTable.server eq server and
                                (SeriesTable.updatedAt less startedAt)
                    }
                    CategoryTable.deleteWhere {
                        CategoryTable.server eq server and
                                (CategoryTable.updatedAt less startedAt)
                    }
                } catch (_: NoSuchElementException) {
                }
            }
        }
    }

    companion object {
        fun ResultRow.toXtreamLiveStream(categoryIdsGroupConcat: CustomFunction<String>? = null) = XtreamLiveStream(
            num = this[LiveStreamTable.num],
            name = this[LiveStreamTable.name],
            streamType = IptvChannelType.live,
            typeName = IptvChannelType.live,
            streamId = this[IptvChannelTable.id].value,
            streamIcon = this[LiveStreamTable.icon],
            server = this[LiveStreamTable.server],
            epgChannelId = this[LiveStreamTable.epgChannelId],
            added = this[LiveStreamTable.added],
            isAdult = this[LiveStreamTable.isAdult],
            categoryId = this[LiveStreamTable.mainCategoryId].toString(),
            categoryIds = categoryIdsGroupConcat?.let { this[categoryIdsGroupConcat].split(",").toList().map { it.toLong() } } ?: emptyList(),
        )
        fun ResultRow.toXtreamMovie(categoryIdsGroupConcat: CustomFunction<String>? = null) = XtreamMovie(
            num = this[MovieTable.num],
            name = this[MovieTable.name],
            streamType = IptvChannelType.movie,
            typeName = IptvChannelType.movie,
            streamId = this[IptvChannelTable.id].value,
            server = this[MovieTable.server],
            added = this[MovieTable.added],
            isAdult = this[MovieTable.isAdult],
            categoryId = this[MovieTable.mainCategoryId].toString(),
            categoryIds = categoryIdsGroupConcat?.let { this[categoryIdsGroupConcat].split(",").toList().map { it.toLong() } } ?: emptyList(),
            streamIcon = this[MovieTable.externalStreamIcon] ?: "",
            rating = this[MovieTable.rating] ?: "",
            rating5Based = this[MovieTable.rating5Based] ?: 0.0,
            tmdb = this[MovieTable.tmdb] ?: "",
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
            categoryIds = categoryIdsGroupConcat?.let { this[categoryIdsGroupConcat].split(",").toList().map { it.toLong() } } ?: emptyList(),
            rating = this[SeriesTable.rating],
            rating5Based = this[SeriesTable.rating5Based],
            tmdb = this[SeriesTable.tmdb] ?: "",
            seriesId = this[SeriesTable.seriesId].toLong(),
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
            parentId = this[CategoryTable.parentId],
        )
    }
}

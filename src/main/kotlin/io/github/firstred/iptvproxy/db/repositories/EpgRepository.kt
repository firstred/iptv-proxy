package io.github.firstred.iptvproxy.db.repositories

import io.github.firstred.iptvproxy.classes.IptvUser
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.tables.ChannelTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgChannelDisplayNameTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgChannelTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeAudioTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeCategoryTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeEpisodeNumberTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammePreviouslyShownTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeRatingTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeSubtitlesTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeTable
import io.github.firstred.iptvproxy.db.tables.sources.XmltvSourceTable
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvAudio
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvAudioStereo
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvDoc
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvEpisodeNum
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvIcon
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgrammePreviouslyShown
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvRating
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvSubtitle
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvSubtitleLanguage
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvText
import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.utils.databaseExpressionTreeLimit
import io.github.firstred.iptvproxy.utils.toListFilters
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.notExists
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import kotlin.math.floor

class EpgRepository {
    fun upsertXmltvSourceForServer(doc: XmltvDoc, server: String) {
        transaction {
            // Upsert the XMLTV source
            XmltvSourceTable.upsert {
                it[XmltvSourceTable.server] = server
                it[XmltvSourceTable.generatorInfoName] = doc.generatorInfoName
                it[XmltvSourceTable.generatorInfoUrl] = doc.generatorInfoUrl
                it[XmltvSourceTable.sourceInfoName] = doc.sourceInfoName
                it[XmltvSourceTable.sourceInfoUrl] = doc.sourceInfoUrl
                it[XmltvSourceTable.sourceInfoLogo] = doc.sourceInfoLogo
            }

            doc.channels?.let { upsertXmltvChannels(it) }

            doc.programmes?.let { upsertXmltvProgrammes(it) }
        }
    }

    fun signalXmltvImportStartedForServer(server: String) {
        transaction {
            XmltvSourceTable.upsert {
                it[XmltvSourceTable.server] = server
                it[XmltvSourceTable.startedImportAt] = Clock.System.now()
            }
        }
    }
    fun signalXmltvImportCompletedForServer(server: String) {
        transaction {
            XmltvSourceTable.upsert {
                it[XmltvSourceTable.server] = server
                it[XmltvSourceTable.completedImportAt] = Clock.System.now()
            }
        }
    }

    fun upsertXmltvChannels(channels: List<XmltvChannel>, clearBefore: Instant? = null) {
        transaction {
            channels.filter { null != it.id }.chunked(config.database.chunkSize.toInt()).forEach { chunk ->
                val epgChannelDisplayNames = mutableMapOf<String, List<XmltvText>>()

                // Refresh all display names - clear them first
                EpgChannelDisplayNameTable.deleteWhere {
                    EpgChannelDisplayNameTable.epgChannelId inList chunk.map { it.id!! }
                }
                if (null != clearBefore) {
                    chunk.chunked(floor(databaseExpressionTreeLimit / 2f).toInt()).forEach { chunk ->
                        // Delete programmes with the given channel IDs that are older than the clearBefore date
                        EpgProgrammeTable.deleteWhere {
                            EpgProgrammeTable.epgChannelId inList chunk.map { it.id!! } and
                                (EpgProgrammeTable.updatedAt less clearBefore)
                        }
                    }
                }

                EpgChannelTable.batchUpsert(
                    data = chunk,
                    shouldReturnGeneratedValues = false,
                ) { channel ->
                    epgChannelDisplayNames[channel.id!!] = channel.displayNames ?: listOf()

                    this[EpgChannelTable.epgChannelId] = channel.id
                    this[EpgChannelTable.icon] = channel.icon?.src
                    this[EpgChannelTable.name] = channel.displayNames?.firstOrNull { null == it.language }?.text
                        ?: channel.displayNames?.firstOrNull()?.text ?: ""
                }

                // Upsert the XMLTV channel display names
                EpgChannelDisplayNameTable.batchUpsert(
                    data = epgChannelDisplayNames.flatMap { (channelId, displayNames) ->
                        displayNames.map { displayName -> Pair(channelId, displayName) }
                    },
                    shouldReturnGeneratedValues = false,
                ) {(channelId, displayName) ->
                    this[EpgChannelDisplayNameTable.epgChannelId] = channelId
                    this[EpgChannelDisplayNameTable.language] = displayName.language ?: ""
                    this[EpgChannelDisplayNameTable.name] = displayName.text ?: ""
                }
            }
        }
    }

    fun upsertXmltvProgrammes(programmes: List<XmltvProgramme>) {
        transaction {
            programmes.chunked(config.database.chunkSize.toInt()).forEach { chunk ->
                val programmeCategories = mutableMapOf<XmltvProgramme, List<XmltvText>>()
                val programmeEpisodes = mutableMapOf<XmltvProgramme, List<XmltvEpisodeNum>>()
                val programmeRatings = mutableMapOf<XmltvProgramme, List<XmltvRating>>()
                val programmePreviouslyShown = mutableMapOf<XmltvProgramme, List<XmltvProgrammePreviouslyShown>>()
                val programmeAudio = mutableMapOf<XmltvProgramme, List<XmltvAudioStereo>>()
                val programmeSubtitles = mutableMapOf<XmltvProgramme, List<XmltvSubtitleLanguage>>()

                EpgProgrammeTable.batchUpsert(
                    data = chunk,
                    shouldReturnGeneratedValues = false,
                ) { programme ->
                    programmeCategories[programme] = programme.category ?: listOf()
                    programmeEpisodes[programme] = programme.episodeNumbers ?: listOf()
                    programmeRatings[programme] = programme.rating ?: listOf()
                    programmePreviouslyShown[programme] = programme.previouslyShown ?: listOf()
                    programmeAudio[programme] = programme.audio?.stereo ?: listOf()
                    programmeSubtitles[programme] = programme.subtitles?.flatMap { it.value ?: listOf() } ?: listOf()

                    this[EpgProgrammeTable.start] = programme.start
                    this[EpgProgrammeTable.stop] = programme.stop
                    this[EpgProgrammeTable.epgChannelId] = programme.channel
                    this[EpgProgrammeTable.title] = programme.title?.text ?: ""
                    this[EpgProgrammeTable.subtitle] = programme.subTitle?.text ?: ""
                    this[EpgProgrammeTable.description] = programme.desc?.text ?: ""
                    this[EpgProgrammeTable.icon] = programme.icon?.src
                    this[EpgProgrammeTable.updatedAt] = Clock.System.now()
                }

                // Upsert the XMLTV programme categories
                EpgProgrammeCategoryTable.batchUpsert(
                    data = programmeCategories.flatMap { (programme, categories) ->
                        categories.map { category -> Triple(programme.channel, programme.start, category) }
                    },
                    shouldReturnGeneratedValues = false,
                ) {(epgChannelId, start, category) ->
                    this[EpgProgrammeCategoryTable.epgChannelId] = epgChannelId
                    this[EpgProgrammeCategoryTable.programmeStart] = start
                    this[EpgProgrammeCategoryTable.language] = category.language ?: ""
                    this[EpgProgrammeCategoryTable.category] = category.text ?: ""
                }

                // Upsert the XMLTV programme episodes
                EpgProgrammeEpisodeNumberTable.batchUpsert(
                    data = programmeEpisodes.flatMap { (programme, episodes) ->
                        episodes.map { episode -> Triple(programme.channel, programme.start, episode) }
                    },
                    shouldReturnGeneratedValues = false,
                ) {(epgChannelId, start, episode) ->
                    this[EpgProgrammeEpisodeNumberTable.epgChannelId] = epgChannelId
                    this[EpgProgrammeEpisodeNumberTable.programmeStart] = start
                    this[EpgProgrammeEpisodeNumberTable.system] = episode.system ?: ""
                    this[EpgProgrammeEpisodeNumberTable.number] = episode.value ?: ""
                }

                // Upsert the XMLTV programme ratings
                EpgProgrammeRatingTable.batchUpsert(
                    data = programmeRatings.flatMap { (programme, ratings) ->
                        ratings.map { rating -> Triple(programme.channel, programme.start, rating) }
                    },
                    shouldReturnGeneratedValues = false,
                ) {(epgChannelId, start, rating) ->
                    this[EpgProgrammeRatingTable.epgChannelId] = epgChannelId
                    this[EpgProgrammeRatingTable.programmeStart] = start
                    this[EpgProgrammeRatingTable.system] = rating.system
                    this[EpgProgrammeRatingTable.rating] = rating.value ?: ""
                }

                // Upsert the XMLTV programme previously shown
                EpgProgrammePreviouslyShownTable.batchUpsert(
                    data = programmePreviouslyShown.flatMap { (programme, previouslyShown) ->
                        previouslyShown.map { shown -> Triple(programme.channel, programme.start, shown) }
                    }.filter { null != it.third.start },
                    shouldReturnGeneratedValues = false,
                ) {(epgChannelId, start, shown) ->
                    this[EpgProgrammePreviouslyShownTable.epgChannelId] = epgChannelId
                    this[EpgProgrammePreviouslyShownTable.programmeStart] = start
                    this[EpgProgrammePreviouslyShownTable.previousStart] = shown.start!!
                }

                // Upsert the XMLTV programme audio
                EpgProgrammeAudioTable.batchUpsert(
                    data = programmeAudio.flatMap { (programme, audio) ->
                        audio.map { audio -> Triple(programme.channel, programme.start, audio) }
                    },
                    shouldReturnGeneratedValues = false,
                ) {(epgChannelId, start, audio) ->
                    this[EpgProgrammeAudioTable.epgChannelId] = epgChannelId
                    this[EpgProgrammeAudioTable.programmeStart] = start
                    this[EpgProgrammeAudioTable.value] = audio.value
                }

                EpgProgrammeSubtitlesTable.batchUpsert(
                    data = programmeSubtitles.flatMap { (programme, subtitles) ->
                        subtitles.map { subtitle -> Triple(programme.channel, programme.start, subtitle) }
                    },
                    shouldReturnGeneratedValues = false,
                ) {(epgChannelId, start, subtitle) ->
                    this[EpgProgrammeSubtitlesTable.epgChannelId] = epgChannelId
                    this[EpgProgrammeSubtitlesTable.programmeStart] = start
                    this[EpgProgrammeSubtitlesTable.language] = subtitle.language ?: ""
                    this[EpgProgrammeSubtitlesTable.subtitle] = subtitle.value ?: ""
                }
            }
        }
    }

    fun forEachEpgChannelChunk(
        chunkSize: Int = config.database.chunkSize.toInt(),
        sortedByName: Boolean = config.sortChannelsByName,
        trimEpg: Boolean = config.trimEpg,
        forUser: IptvUser? = null,
        action: (List<XmltvChannel>) -> Unit,
    ) {
        var offset = 0L

        val usedIds = findAllUsedEpgChannelIds(user = forUser)

        do {
            val epgChannelQuery = EpgChannelTable
                .selectAll()
                .groupBy(EpgChannelTable.epgChannelId)
            if (trimEpg) epgChannelQuery.andWhere { EpgChannelTable.epgChannelId inList usedIds }
            if (sortedByName) {
                epgChannelQuery.orderBy(EpgChannelTable.name to SortOrder.ASC)
            } else {
                epgChannelQuery.orderBy(EpgChannelTable.epgChannelId to SortOrder.ASC)
            }
            epgChannelQuery
                .limit(chunkSize)
                .offset(offset)
            val channels: MutableList<XmltvChannel> = transaction {
                epgChannelQuery.map { it.toXmltvChannel() }.toMutableList()
            }

            if (channels.isEmpty()) break

            transaction {
                val channelDisplayNameQuery = EpgChannelDisplayNameTable
                    .selectAll()
                    .where { EpgChannelDisplayNameTable.epgChannelId inList channels.filter { null != it.id}.map { it.id!! } }
                channelDisplayNameQuery.forEach { row ->
                    channels.indexOfFirst { it.id == row[EpgChannelDisplayNameTable.epgChannelId] }.let { idx ->
                        val it = channels[idx]
                        channels[idx] = it.copy(
                            displayNames = (it.displayNames ?: listOf()) + XmltvText(
                                language = row[EpgChannelDisplayNameTable.language].ifBlank { null },
                                text = row[EpgChannelDisplayNameTable.name],
                            )
                        )
                    }
                }
            }

            action(channels.toList())
            offset += chunkSize
        } while (channels.isNotEmpty())
    }

    fun forEachEpgProgrammeChunk(
        chunkSize: Int = config.database.chunkSize.toInt(),
        forUser: IptvUser? = null,
        action: (List<XmltvProgramme>) -> Unit,
    ) {
        var offset = 0L

        val usedIds = findAllUsedEpgChannelIds(user = forUser)

        do {
            val programmeQuery = EpgProgrammeTable
                .selectAll()
                .andWhere { EpgProgrammeTable.epgChannelId inList usedIds }
            programmeQuery
                .groupBy(EpgProgrammeTable.epgChannelId, EpgProgrammeTable.start)
                .orderBy(EpgProgrammeTable.epgChannelId to SortOrder.ASC, EpgProgrammeTable.start to SortOrder.ASC)
                .limit(chunkSize)
                .offset(offset)

            val programmes = transaction {
                programmeQuery
                    .associateBy(
                        { Pair(it[EpgProgrammeTable.epgChannelId], it[EpgProgrammeTable.start]) },
                        { it.toXmltvProgramme() },
                    )
                    .toMutableMap()
            }

            if (programmes.isEmpty()) break

            val epgChannelIds = programmes.keys.map { it.first }

            // Programme category
            transaction {
                val programmeCategoryQuery = EpgProgrammeTable
                    .join(
                        EpgProgrammeCategoryTable,
                        JoinType.LEFT,
                        onColumn = EpgProgrammeTable.epgChannelId,
                        otherColumn = EpgProgrammeCategoryTable.epgChannelId,
                        additionalConstraint = { EpgProgrammeCategoryTable.programmeStart eq EpgProgrammeTable.start }
                    )
                    .select(
                        EpgProgrammeCategoryTable.epgChannelId,
                        EpgProgrammeCategoryTable.programmeStart,
                        EpgProgrammeCategoryTable.language,
                        EpgProgrammeCategoryTable.category,
                    )
                    .where { EpgProgrammeCategoryTable.epgChannelId inList epgChannelIds }
                    .orderBy(
                        EpgProgrammeTable.epgChannelId to SortOrder.ASC,
                        EpgProgrammeTable.start to SortOrder.ASC
                    )
                    .limit(chunkSize)
                    .offset(offset)

                programmeCategoryQuery.forEach { row ->
                    programmes[Pair(row[EpgProgrammeCategoryTable.epgChannelId], row[EpgProgrammeCategoryTable.programmeStart])]?.let { programme ->
                        programmes[Pair(row[EpgProgrammeCategoryTable.epgChannelId], row[EpgProgrammeCategoryTable.programmeStart])] = programme.copy(
                            category = (programme.category ?: listOf()) + XmltvText(
                                language = row[EpgProgrammeCategoryTable.language].ifBlank { null },
                                text = row[EpgProgrammeCategoryTable.category],
                            )
                        )
                    }
                }
            }

            // Programme audio
            transaction {
                val programmeAudioTableQuery = EpgProgrammeTable
                    .join(
                        EpgProgrammeAudioTable,
                        JoinType.LEFT,
                        onColumn = EpgProgrammeTable.epgChannelId,
                        otherColumn = EpgProgrammeAudioTable.epgChannelId,
                        additionalConstraint = { EpgProgrammeAudioTable.programmeStart eq EpgProgrammeTable.start }
                    )
                    .select(
                        EpgProgrammeAudioTable.epgChannelId,
                        EpgProgrammeAudioTable.programmeStart,
                        EpgProgrammeAudioTable.type,
                        EpgProgrammeAudioTable.value,
                    )
                    .where { EpgProgrammeAudioTable.epgChannelId inList epgChannelIds }
                    .orderBy(
                        EpgProgrammeTable.epgChannelId to SortOrder.ASC,
                        EpgProgrammeTable.start to SortOrder.ASC
                    )
                    .limit(chunkSize)
                    .offset(offset)

                programmeAudioTableQuery.forEach { row ->
                    programmes[Pair(row[EpgProgrammeAudioTable.epgChannelId], row[EpgProgrammeAudioTable.programmeStart])]?.let { programme ->
                        programmes[Pair(row[EpgProgrammeAudioTable.epgChannelId], row[EpgProgrammeAudioTable.programmeStart])] = programme.copy(
                            audio = (programme.audio ?: XmltvAudio()).copy(
                                stereo = (programme.audio?.stereo ?: listOf()) + XmltvAudioStereo(
                                    value = row[EpgProgrammeAudioTable.value],
                                )
                            )
                        )
                    }
                }
            }

            // Programme episode numbers
            transaction {
                val programmeEpisodeNumberTableQuery = EpgProgrammeTable
                    .join(
                        EpgProgrammeEpisodeNumberTable,
                        JoinType.LEFT,
                        onColumn = EpgProgrammeTable.epgChannelId,
                        otherColumn = EpgProgrammeEpisodeNumberTable.epgChannelId,
                        additionalConstraint = { EpgProgrammeEpisodeNumberTable.programmeStart eq EpgProgrammeTable.start }
                    )
                    .select(
                        EpgProgrammeEpisodeNumberTable.epgChannelId,
                        EpgProgrammeEpisodeNumberTable.programmeStart,
                        EpgProgrammeEpisodeNumberTable.system,
                        EpgProgrammeEpisodeNumberTable.number,
                    )
                    .where { EpgProgrammeEpisodeNumberTable.epgChannelId inList epgChannelIds }
                    .orderBy(
                        EpgProgrammeTable.epgChannelId to SortOrder.ASC,
                        EpgProgrammeTable.start to SortOrder.ASC
                    )
                    .limit(chunkSize)
                    .offset(offset)

                programmeEpisodeNumberTableQuery.forEach { row ->
                    programmes[Pair(row[EpgProgrammeEpisodeNumberTable.epgChannelId], row[EpgProgrammeEpisodeNumberTable.programmeStart])]?.let { programme ->
                        programmes[Pair(row[EpgProgrammeEpisodeNumberTable.epgChannelId], row[EpgProgrammeEpisodeNumberTable.programmeStart])] = programme.copy(
                            episodeNumbers = (programme.episodeNumbers ?: listOf()) + XmltvEpisodeNum(
                                system = row[EpgProgrammeEpisodeNumberTable.system]?.ifBlank { null },
                                value = row[EpgProgrammeEpisodeNumberTable.number],
                            )
                        )
                    }
                }
            }

            // Programme previously shown
            transaction {
                val programmePreviouslyShownTableQuery = EpgProgrammeTable
                    .join(
                        EpgProgrammePreviouslyShownTable,
                        JoinType.LEFT,
                        onColumn = EpgProgrammeTable.epgChannelId,
                        otherColumn = EpgProgrammePreviouslyShownTable.epgChannelId,
                        additionalConstraint = { EpgProgrammePreviouslyShownTable.programmeStart eq EpgProgrammeTable.start }
                    )
                    .select(
                        EpgProgrammePreviouslyShownTable.epgChannelId,
                        EpgProgrammePreviouslyShownTable.programmeStart,
                        EpgProgrammePreviouslyShownTable.previousStart,
                    )
                    .where { EpgProgrammePreviouslyShownTable.epgChannelId inList epgChannelIds }
                    .orderBy(
                        EpgProgrammeTable.epgChannelId to SortOrder.ASC,
                        EpgProgrammeTable.start to SortOrder.ASC
                    )
                    .limit(chunkSize)
                    .offset(offset)

                programmePreviouslyShownTableQuery.forEach { row ->
                    programmes[Pair(row[EpgProgrammePreviouslyShownTable.epgChannelId], row[EpgProgrammePreviouslyShownTable.programmeStart])]?.let { programme ->
                        programmes[Pair(row[EpgProgrammePreviouslyShownTable.epgChannelId], row[EpgProgrammePreviouslyShownTable.programmeStart])] = programme.copy(
                            previouslyShown = (programme.previouslyShown ?: listOf()) + XmltvProgrammePreviouslyShown(
                                start = row[EpgProgrammePreviouslyShownTable.previousStart],
                            )
                        )
                    }
                }
            }

            // Programme ratings
            transaction {
                val programmeRatingTableQuery = EpgProgrammeTable
                    .join(
                        EpgProgrammeRatingTable,
                        JoinType.LEFT,
                        onColumn = EpgProgrammeTable.epgChannelId,
                        otherColumn = EpgProgrammeRatingTable.epgChannelId,
                        additionalConstraint = { EpgProgrammeRatingTable.programmeStart eq EpgProgrammeTable.start }
                    )
                    .select(
                        EpgProgrammeRatingTable.epgChannelId,
                        EpgProgrammeRatingTable.programmeStart,
                        EpgProgrammeRatingTable.system,
                        EpgProgrammeRatingTable.rating,
                    )
                    .where { EpgProgrammeRatingTable.epgChannelId inList epgChannelIds }
                    .orderBy(
                        EpgProgrammeTable.epgChannelId to SortOrder.ASC,
                        EpgProgrammeTable.start to SortOrder.ASC
                    )
                    .limit(chunkSize)
                    .offset(offset)

                programmeRatingTableQuery.forEach { row ->
                    programmes[Pair(row[EpgProgrammeRatingTable.epgChannelId], row[EpgProgrammeRatingTable.programmeStart])]?.let { programme ->
                        programmes[Pair(row[EpgProgrammeRatingTable.epgChannelId], row[EpgProgrammeRatingTable.programmeStart])] = programme.copy(
                            rating = (programme.rating ?: listOf()) + XmltvRating(
                                system = row[EpgProgrammeRatingTable.system],
                                value = row[EpgProgrammeRatingTable.rating],
                            )
                        )
                    }
                }
            }

            // Programme subtitles
            transaction {
                val programmeSubtitleTableQuery = EpgProgrammeTable
                    .join(
                        EpgProgrammeSubtitlesTable,
                        JoinType.LEFT,
                        onColumn = EpgProgrammeTable.epgChannelId,
                        otherColumn = EpgProgrammeSubtitlesTable.epgChannelId,
                        additionalConstraint = { EpgProgrammeSubtitlesTable.programmeStart eq EpgProgrammeTable.start }
                    )
                    .select(
                        EpgProgrammeSubtitlesTable.epgChannelId,
                        EpgProgrammeSubtitlesTable.programmeStart,
                        EpgProgrammeSubtitlesTable.language,
                        EpgProgrammeSubtitlesTable.subtitle,
                    )
                    .where { EpgProgrammeSubtitlesTable.epgChannelId inList epgChannelIds }
                    .orderBy(
                        EpgProgrammeTable.epgChannelId to SortOrder.ASC,
                        EpgProgrammeTable.start to SortOrder.ASC
                    )
                    .limit(chunkSize)
                    .offset(offset)

                programmeSubtitleTableQuery.forEach { row ->
                    programmes[Pair(row[EpgProgrammeSubtitlesTable.epgChannelId], row[EpgProgrammeSubtitlesTable.programmeStart])]?.let { programme ->
                        programmes[Pair( row[EpgProgrammeSubtitlesTable.epgChannelId], row[EpgProgrammeSubtitlesTable.programmeStart])] = programme.copy(
                            subtitles = (programme.subtitles ?: listOf()) + XmltvSubtitle(
                                type = "",
                                value = listOf(
                                    XmltvSubtitleLanguage(
                                        language = row[EpgProgrammeSubtitlesTable.language],
                                        value = row[EpgProgrammeSubtitlesTable.subtitle]
                                    )
                                ),
                            )
                        )
                    }
                }
            }

            action(programmes.values.toList())
            offset += chunkSize
        } while (programmes.isNotEmpty())
    }

    fun getEpgIdForChannelId(channelId: UInt): String? = transaction {
        ChannelTable
            .select(ChannelTable.epgChannelId)
            .where { ChannelTable.id eq channelId }
            .filter { null != it[ChannelTable.epgChannelId] }
            .map { it[ChannelTable.epgChannelId] }
            .firstOrNull()
    }

    fun getProgrammesForChannelId(
        channelId: UInt,
        count: Int = 4,
        now: Instant = Clock.System.now(),
    ): List<XmltvProgramme> = transaction {
        val epgId = getEpgIdForChannelId(channelId) ?: return@transaction emptyList()
        // Try the preferred server first
        val query = EpgProgrammeTable
            .join(
                ChannelTable,
                JoinType.INNER,
                onColumn = EpgProgrammeTable.epgChannelId,
                otherColumn = ChannelTable.epgChannelId,
            )
            .selectAll()
            .andWhere {
                EpgProgrammeTable.epgChannelId eq epgId and
                    (EpgProgrammeTable.start greaterEq now) or (
                    (EpgProgrammeTable.start lessEq now) and (EpgProgrammeTable.stop greaterEq now)
                )
            }
            .orderBy(EpgProgrammeTable.start to SortOrder.ASC)
        if (count < Int.MAX_VALUE) query.limit(count)
        val results = query.map { it.toXmltvProgramme() }

        if (results.isNotEmpty()) return@transaction results

        emptyList()
    }

    fun findAllUsedEpgChannelIds(user: IptvUser? = null): List<String> = transaction {
        val channelQuery = ChannelTable.select(ChannelTable.epgChannelId)
        user?.let {
            it.toListFilters().applyToQuery(channelQuery, ChannelTable.name, ChannelTable.mainGroup)
            if (!it.moviesEnabled) channelQuery.andWhere { ChannelTable.type neq IptvChannelType.movie }
            if (!it.seriesEnabled) channelQuery.andWhere { ChannelTable.type neq IptvChannelType.series }
        }
        channelQuery
            .withDistinct(true)
            .mapNotNull { it[ChannelTable.epgChannelId]?.ifBlank { null } }
            .distinct()
    }

    fun cleanup() {
        val now = Clock.System.now()
        transaction {
            // Delete XMLTV sources where the server no longer exists
            XmltvSourceTable.deleteWhere {
                XmltvSourceTable.server notInList config.servers.map { it.name }
            }

            // Delete channels that have not been updated for a long time
            EpgChannelTable.deleteWhere {
                EpgChannelTable.updatedAt less ((now - config.channelMaxStalePeriod).coerceAtLeast(Instant.DISTANT_PAST))
            }

            // Clear programme table where the channel ID no longer exists
            EpgProgrammeTable.deleteWhere {
                notExists(
                    EpgChannelTable
                        .join(
                            EpgProgrammeTable,
                            JoinType.INNER,
                            onColumn = EpgChannelTable.epgChannelId,
                            otherColumn = EpgProgrammeTable.epgChannelId,
                        )
                        .select(EpgChannelTable.epgChannelId, EpgProgrammeTable.epgChannelId)
                        .where {
                            EpgChannelTable.epgChannelId eq EpgProgrammeTable.epgChannelId
                        }
                )
            }

            // Clear programmes that are too old for any server
            val oldestAllowed = (now - config.servers.maxOf { it.epgBefore }).coerceAtLeast(Instant.DISTANT_PAST)
            EpgProgrammeTable.deleteWhere {
                EpgProgrammeTable.updatedAt less oldestAllowed
            }

            EpgProgrammeTable.deleteWhere {
                notExists(
                    EpgChannelTable
                        .join(
                            EpgProgrammeTable,
                            JoinType.INNER,
                            onColumn = EpgChannelTable.epgChannelId,
                            otherColumn = EpgProgrammeTable.epgChannelId,
                        )
                        .select(EpgChannelTable.epgChannelId, EpgProgrammeTable.epgChannelId)
                        .where {
                            EpgChannelTable.epgChannelId eq EpgProgrammeTable.epgChannelId
                        }
                )
            }
        }
    }

    companion object {
        private fun ResultRow.toXmltvChannel() = XmltvChannel(
            id = this[EpgChannelTable.epgChannelId],
            displayNames = listOf(),
            icon = this[EpgChannelTable.icon]?.let { src -> XmltvIcon(src = src) },
        )
        private fun ResultRow.toXmltvProgramme() = XmltvProgramme(
            start = this[EpgProgrammeTable.start],
            stop = this[EpgProgrammeTable.stop],
            channel = this[EpgProgrammeTable.epgChannelId],
            title = XmltvText(
                language = null,
                text = this[EpgProgrammeTable.title],
            ),
            subTitle = XmltvText(
                language = null,
                text = this[EpgProgrammeTable.subtitle],
            ),
            desc = XmltvText(
                language = null,
                text = this[EpgProgrammeTable.description],
            ),
            icon = this[EpgProgrammeTable.icon]?.let { src -> XmltvIcon(src = src) },
        )
    }
}

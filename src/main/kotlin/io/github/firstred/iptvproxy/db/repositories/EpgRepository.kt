package io.github.firstred.iptvproxy.db.repositories

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
import io.github.firstred.iptvproxy.utils.NTuple4
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

class EpgRepository {
    fun upsertXmltvSourceForServer(doc: XmltvDoc, server: String) {
        transaction {
            // Upsert the XMLTV source
            XmltvSourceTable.upsert {
                it[XmltvSourceTable.server] = server
                it[XmltvSourceTable.generatorInfoName] = doc.generatorInfoName
                it[XmltvSourceTable.generatorInfoUrl] = doc.generatorInfoUrl
                it[XmltvSourceTable.sourceInfoUrl] = doc.sourceInfoUrl
                it[XmltvSourceTable.sourceInfoName] = doc.sourceInfoName
                it[XmltvSourceTable.sourceInfoLogo] = doc.sourceInfoLogo
            }

            // Delete old and insert new channels
            EpgChannelTable.deleteWhere {
                EpgChannelTable.server eq server
            }
            EpgChannelDisplayNameTable.deleteWhere {
                EpgChannelDisplayNameTable.server eq server
            }
            doc.channels?.let { upsertXmltvChannelsForServer(it, server) }

            // Delete old and insert new programmes
            EpgProgrammeTable.deleteWhere {
                EpgProgrammeTable.server eq server
            }
            doc.programmes?.let { upsertXmltvProgrammesForServer(it, server) }
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

    fun upsertXmltvChannelsForServer(channels: List<XmltvChannel>, server: String) {
        transaction {
            channels.filter { null != it.id }.chunked(config.database.chunkSize.toInt()).forEach { chunk ->
                val epgChannelDisplayNames = mutableMapOf<String, List<XmltvText>>()

                EpgChannelTable.batchUpsert(
                    data = chunk,
                    shouldReturnGeneratedValues = false,
                ) { channel ->
                    epgChannelDisplayNames[channel.id!!] = channel.displayNames ?: listOf()

                    this[EpgChannelTable.server] = server
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
                    this[EpgChannelDisplayNameTable.server] = server
                    this[EpgChannelDisplayNameTable.epgChannelId] = channelId
                    this[EpgChannelDisplayNameTable.language] = displayName.language ?: ""
                    this[EpgChannelDisplayNameTable.name] = displayName.text ?: ""
                }
            }
        }
    }

    fun upsertXmltvProgrammesForServer(programmes: List<XmltvProgramme>, server: String) {
        transaction {
            programmes.chunked(config.database.chunkSize.toInt()).forEach { chunk ->
                val programmeCategories = mutableMapOf<Pair<String, XmltvProgramme>, List<XmltvText>>()
                val programmeEpisodes = mutableMapOf<Pair<String, XmltvProgramme>, List<XmltvEpisodeNum>>()
                val programmeRatings = mutableMapOf<Pair<String, XmltvProgramme>, List<XmltvRating>>()
                val programmePreviouslyShown = mutableMapOf<Pair<String, XmltvProgramme>, List<XmltvProgrammePreviouslyShown>>()
                val programmeAudio = mutableMapOf<Pair<String, XmltvProgramme>, List<XmltvAudioStereo>>()
                val programmeSubtitles = mutableMapOf<Pair<String, XmltvProgramme>, List<XmltvSubtitleLanguage>>()

                EpgProgrammeTable.batchUpsert(
                    data = chunk,
                    shouldReturnGeneratedValues = false,
                ) { programme ->
                    programmeCategories[Pair(server ,programme)] = programme.category ?: listOf()
                    programmeEpisodes[Pair(server ,programme)] = programme.episodeNumbers ?: listOf()
                    programmeRatings[Pair(server ,programme)] = programme.rating ?: listOf()
                    programmePreviouslyShown[Pair(server ,programme)] = programme.previouslyShown ?: listOf()
                    programmeAudio[Pair(server ,programme)] = programme.audio?.stereo ?: listOf()
                    programmeSubtitles[Pair(server ,programme)] = programme.subtitles?.flatMap { it.value ?: listOf() } ?: listOf()

                    this[EpgProgrammeTable.server] = server
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
                    data = programmeCategories.flatMap { (pair, categories) ->
                        val server = pair.first
                        val programme = pair.second
                        categories.map { category -> NTuple4(server, programme.channel, programme.start, category) }
                    },
                    shouldReturnGeneratedValues = false,
                ) {(server, epgChannelId, start, category) ->
                    this[EpgProgrammeCategoryTable.server] = server
                    this[EpgProgrammeCategoryTable.epgChannelId] = epgChannelId
                    this[EpgProgrammeCategoryTable.programmeStart] = start
                    this[EpgProgrammeCategoryTable.language] = category.language ?: ""
                    this[EpgProgrammeCategoryTable.category] = category.text ?: ""
                }

                // Upsert the XMLTV programme episodes
                EpgProgrammeEpisodeNumberTable.batchUpsert(
                    data = programmeEpisodes.flatMap { (pair, episodes) ->
                        val server = pair.first
                        val programme = pair.second
                        episodes.map { episode -> NTuple4(server, programme.channel, programme.start, episode) }
                    },
                    shouldReturnGeneratedValues = false,
                ) {(server, epgChannelId, start, episode) ->
                    this[EpgProgrammeEpisodeNumberTable.server] = server
                    this[EpgProgrammeEpisodeNumberTable.epgChannelId] = epgChannelId
                    this[EpgProgrammeEpisodeNumberTable.programmeStart] = start
                    this[EpgProgrammeEpisodeNumberTable.system] = episode.system ?: ""
                    this[EpgProgrammeEpisodeNumberTable.number] = episode.value ?: ""
                }

                // Upsert the XMLTV programme ratings
                EpgProgrammeRatingTable.batchUpsert(
                    data = programmeRatings.flatMap { (pair, ratings) ->
                        val server = pair.first
                        val programme = pair.second
                        ratings.map { rating -> NTuple4(server, programme.channel, programme.start, rating) }
                    },
                    shouldReturnGeneratedValues = false,
                ) {(server, epgChannelId, start, rating) ->
                    this[EpgProgrammeRatingTable.server] = server
                    this[EpgProgrammeRatingTable.epgChannelId] = epgChannelId
                    this[EpgProgrammeRatingTable.programmeStart] = start
                    this[EpgProgrammeRatingTable.system] = rating.system
                    this[EpgProgrammeRatingTable.rating] = rating.value ?: ""
                }

                // Upsert the XMLTV programme previously shown
                EpgProgrammePreviouslyShownTable.batchUpsert(
                    data = programmePreviouslyShown.flatMap { (pair, previouslyShown) ->
                        val server = pair.first
                        val programme = pair.second
                        previouslyShown.map { shown -> NTuple4(server, programme.channel, programme.start, shown) }
                    }.filter { null != it.fourth.start },
                    shouldReturnGeneratedValues = false,
                ) {(server, epgChannelId, start, shown) ->
                    this[EpgProgrammePreviouslyShownTable.server] = server
                    this[EpgProgrammePreviouslyShownTable.epgChannelId] = epgChannelId
                    this[EpgProgrammePreviouslyShownTable.programmeStart] = start
                    this[EpgProgrammePreviouslyShownTable.previousStart] = shown.start!!
                }

                // Upsert the XMLTV programme audio
                EpgProgrammeAudioTable.batchUpsert(
                    data = programmeAudio.flatMap { (pair, audio) ->
                        val server = pair.first
                        val programme = pair.second
                        audio.map { audio -> NTuple4(server, programme.channel, programme.start, audio) }
                    },
                    shouldReturnGeneratedValues = false,
                ) {(server, epgChannelId, start, audio) ->
                    this[EpgProgrammeAudioTable.server] = server
                    this[EpgProgrammeAudioTable.epgChannelId] = epgChannelId
                    this[EpgProgrammeAudioTable.programmeStart] = start
                    this[EpgProgrammeAudioTable.value] = audio.value
                }

                EpgProgrammeSubtitlesTable.batchUpsert(
                    data = programmeSubtitles.flatMap { (pair, subtitles) ->
                        val server = pair.first
                        val programme = pair.second
                        subtitles.map { subtitle -> NTuple4(server, programme.channel, programme.start, subtitle) }
                    },
                    shouldReturnGeneratedValues = false,
                ) {(server, epgChannelId, start, subtitle) ->
                    this[EpgProgrammeSubtitlesTable.server] = server
                    this[EpgProgrammeSubtitlesTable.epgChannelId] = epgChannelId
                    this[EpgProgrammeSubtitlesTable.programmeStart] = start
                    this[EpgProgrammeSubtitlesTable.language] = subtitle.language ?: ""
                    this[EpgProgrammeSubtitlesTable.subtitle] = subtitle.value ?: ""
                }
            }
        }
    }

    fun forEachEpgChannelChunk(
        server: String? = null,
        chunkSize: Int = config.database.chunkSize.toInt(),
        sortedByName: Boolean = config.sortChannelsByName,
        action: (List<XmltvChannel>) -> Unit,
    ) {
        var offset = 0L

        do {
            val channelQuery = EpgChannelTable.selectAll()
            server?.let { channelQuery.where { EpgChannelTable.server eq it } }
            if (sortedByName) {
                channelQuery.orderBy(EpgChannelTable.name to SortOrder.ASC)
            } else {
                channelQuery.orderBy(EpgChannelTable.server to SortOrder.ASC, EpgChannelTable.epgChannelId to SortOrder.ASC)
            }
            channelQuery
                .limit(chunkSize)
                .offset(offset)
            val channels: MutableList<XmltvChannel> = transaction {
                channelQuery.map { it.toXmltvChannel() }.toMutableList()
            }

            if (channels.isEmpty()) break

            transaction {
                val channelDisplayNameQuery = EpgChannelDisplayNameTable
                    .selectAll()
                    .where { EpgChannelDisplayNameTable.epgChannelId inList channels.filter { null != it.id}.map { it.id!! } }
                server?.let { channelDisplayNameQuery.andWhere { EpgChannelDisplayNameTable.server eq it } }
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
        server: String? = null,
        chunkSize: Int = config.database.chunkSize.toInt(),
        action: (List<XmltvProgramme>) -> Unit,
    ) {
        var offset = 0L

        do {
            val programmeQuery = EpgProgrammeTable.selectAll()
            server?.let { programmeQuery.where { EpgProgrammeTable.server eq it } }
            programmeQuery
                .orderBy(EpgProgrammeTable.server to SortOrder.ASC, EpgProgrammeTable.epgChannelId to SortOrder.ASC, EpgProgrammeTable.start to SortOrder.ASC)
                .limit(chunkSize)
                .offset(offset)

            val programmes = transaction {
                programmeQuery
                    .associateBy(
                        { Triple(it[EpgProgrammeTable.server], it[EpgProgrammeTable.epgChannelId], it[EpgProgrammeTable.start]) },
                        { it.toXmltvProgramme() },
                    )
                    .toMutableMap()
            }

            if (programmes.isEmpty()) break

            // Programme category
            transaction {
                val programmeCategoryQuery = EpgProgrammeTable
                    .join(
                        EpgProgrammeCategoryTable,
                        JoinType.LEFT,
                        onColumn = EpgProgrammeTable.epgChannelId,
                        otherColumn = EpgProgrammeCategoryTable.epgChannelId,
                        additionalConstraint = { (EpgProgrammeCategoryTable.server eq EpgProgrammeTable.server) and (EpgProgrammeCategoryTable.programmeStart eq EpgProgrammeTable.start) }
                    )
                    .select(
                        EpgProgrammeCategoryTable.server,
                        EpgProgrammeCategoryTable.epgChannelId,
                        EpgProgrammeCategoryTable.programmeStart,
                        EpgProgrammeCategoryTable.language,
                        EpgProgrammeCategoryTable.category,
                    )
                server?.let { programmeCategoryQuery.where { EpgProgrammeTable.server eq it } }
                programmeCategoryQuery
                    .orderBy(
                        EpgProgrammeTable.server to SortOrder.ASC,
                        EpgProgrammeTable.epgChannelId to SortOrder.ASC,
                        EpgProgrammeTable.start to SortOrder.ASC
                    )
                    .limit(chunkSize)
                    .offset(offset)

                programmeCategoryQuery.forEach { row ->
                    programmes[Triple(row[EpgProgrammeCategoryTable.server], row[EpgProgrammeCategoryTable.epgChannelId], row[EpgProgrammeCategoryTable.programmeStart])]?.let { programme ->
                        programmes[Triple(row[EpgProgrammeCategoryTable.server], row[EpgProgrammeCategoryTable.epgChannelId], row[EpgProgrammeCategoryTable.programmeStart])] = programme.copy(
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
                        additionalConstraint = { (EpgProgrammeAudioTable.server eq EpgProgrammeTable.server) and (EpgProgrammeAudioTable.programmeStart eq EpgProgrammeTable.start) }
                    )
                    .select(
                        EpgProgrammeAudioTable.server,
                        EpgProgrammeAudioTable.epgChannelId,
                        EpgProgrammeAudioTable.programmeStart,
                        EpgProgrammeAudioTable.type,
                        EpgProgrammeAudioTable.value,
                    )
                server?.let { programmeAudioTableQuery.where { EpgProgrammeTable.server eq it } }
                programmeAudioTableQuery
                    .orderBy(
                        EpgProgrammeTable.server to SortOrder.ASC,
                        EpgProgrammeTable.epgChannelId to SortOrder.ASC,
                        EpgProgrammeTable.start to SortOrder.ASC
                    )
                    .limit(chunkSize)
                    .offset(offset)

                programmeAudioTableQuery.forEach { row ->
                    programmes[Triple(row[EpgProgrammeAudioTable.server], row[EpgProgrammeAudioTable.epgChannelId], row[EpgProgrammeAudioTable.programmeStart])]?.let { programme ->
                        programmes[Triple(row[EpgProgrammeAudioTable.server], row[EpgProgrammeAudioTable.epgChannelId], row[EpgProgrammeAudioTable.programmeStart])] = programme.copy(
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
                        additionalConstraint = { (EpgProgrammeEpisodeNumberTable.server eq EpgProgrammeTable.server) and (EpgProgrammeEpisodeNumberTable.programmeStart eq EpgProgrammeTable.start) }
                    )
                    .select(
                        EpgProgrammeEpisodeNumberTable.server,
                        EpgProgrammeEpisodeNumberTable.epgChannelId,
                        EpgProgrammeEpisodeNumberTable.programmeStart,
                        EpgProgrammeEpisodeNumberTable.system,
                        EpgProgrammeEpisodeNumberTable.number,
                    )
                server?.let { programmeEpisodeNumberTableQuery.where { EpgProgrammeTable.server eq it } }
                programmeEpisodeNumberTableQuery
                    .orderBy(
                        EpgProgrammeTable.server to SortOrder.ASC,
                        EpgProgrammeTable.epgChannelId to SortOrder.ASC,
                        EpgProgrammeTable.start to SortOrder.ASC
                    )
                    .limit(chunkSize)
                    .offset(offset)

                programmeEpisodeNumberTableQuery.forEach { row ->
                    programmes[Triple(row[EpgProgrammeEpisodeNumberTable.server], row[EpgProgrammeEpisodeNumberTable.epgChannelId], row[EpgProgrammeEpisodeNumberTable.programmeStart])]?.let { programme ->
                        programmes[Triple(row[EpgProgrammeEpisodeNumberTable.server], row[EpgProgrammeEpisodeNumberTable.epgChannelId], row[EpgProgrammeEpisodeNumberTable.programmeStart])] = programme.copy(
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
                        additionalConstraint = { (EpgProgrammePreviouslyShownTable.server eq EpgProgrammeTable.server) and (EpgProgrammePreviouslyShownTable.programmeStart eq EpgProgrammeTable.start) }
                    )
                    .select(
                        EpgProgrammePreviouslyShownTable.server,
                        EpgProgrammePreviouslyShownTable.epgChannelId,
                        EpgProgrammePreviouslyShownTable.programmeStart,
                        EpgProgrammePreviouslyShownTable.previousStart,
                    )
                server?.let { programmePreviouslyShownTableQuery.where { EpgProgrammeTable.server eq it } }
                programmePreviouslyShownTableQuery
                    .orderBy(
                        EpgProgrammeTable.server to SortOrder.ASC,
                        EpgProgrammeTable.epgChannelId to SortOrder.ASC,
                        EpgProgrammeTable.start to SortOrder.ASC
                    )
                    .limit(chunkSize)
                    .offset(offset)

                programmePreviouslyShownTableQuery.forEach { row ->
                    programmes[Triple(row[EpgProgrammePreviouslyShownTable.server], row[EpgProgrammePreviouslyShownTable.epgChannelId], row[EpgProgrammePreviouslyShownTable.programmeStart])]?.let { programme ->
                        programmes[Triple(row[EpgProgrammePreviouslyShownTable.server], row[EpgProgrammePreviouslyShownTable.epgChannelId], row[EpgProgrammePreviouslyShownTable.programmeStart])] = programme.copy(
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
                        additionalConstraint = { (EpgProgrammeRatingTable.server eq EpgProgrammeTable.server) and (EpgProgrammeRatingTable.programmeStart eq EpgProgrammeTable.start) }
                    )
                    .select(
                        EpgProgrammeRatingTable.server,
                        EpgProgrammeRatingTable.epgChannelId,
                        EpgProgrammeRatingTable.programmeStart,
                        EpgProgrammeRatingTable.system,
                        EpgProgrammeRatingTable.rating,
                    )
                server?.let { programmeRatingTableQuery.where { EpgProgrammeTable.server eq it } }
                programmeRatingTableQuery
                    .orderBy(
                        EpgProgrammeTable.server to SortOrder.ASC,
                        EpgProgrammeTable.epgChannelId to SortOrder.ASC,
                        EpgProgrammeTable.start to SortOrder.ASC
                    )
                    .limit(chunkSize)
                    .offset(offset)

                programmeRatingTableQuery.forEach { row ->
                    programmes[Triple(row[EpgProgrammeRatingTable.server], row[EpgProgrammeRatingTable.epgChannelId], row[EpgProgrammeRatingTable.programmeStart])]?.let { programme ->
                        programmes[Triple(row[EpgProgrammeRatingTable.server], row[EpgProgrammeRatingTable.epgChannelId], row[EpgProgrammeRatingTable.programmeStart])] = programme.copy(
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
                        additionalConstraint = { (EpgProgrammeSubtitlesTable.server eq EpgProgrammeTable.server) and (EpgProgrammeSubtitlesTable.programmeStart eq EpgProgrammeTable.start) }
                    )
                    .select(
                        EpgProgrammeSubtitlesTable.server,
                        EpgProgrammeSubtitlesTable.epgChannelId,
                        EpgProgrammeSubtitlesTable.programmeStart,
                        EpgProgrammeSubtitlesTable.language,
                        EpgProgrammeSubtitlesTable.subtitle,
                    )
                server?.let { programmeSubtitleTableQuery.where { EpgProgrammeTable.server eq it } }
                programmeSubtitleTableQuery
                    .orderBy(
                        EpgProgrammeTable.server to SortOrder.ASC,
                        EpgProgrammeTable.epgChannelId to SortOrder.ASC,
                        EpgProgrammeTable.start to SortOrder.ASC
                    )
                    .limit(chunkSize)
                    .offset(offset)

                programmeSubtitleTableQuery.forEach { row ->
                    programmes[Triple(row[EpgProgrammeSubtitlesTable.server], row[EpgProgrammeSubtitlesTable.epgChannelId], row[EpgProgrammeSubtitlesTable.programmeStart])]?.let { programme ->
                        programmes[Triple(row[EpgProgrammeSubtitlesTable.server], row[EpgProgrammeSubtitlesTable.epgChannelId], row[EpgProgrammeSubtitlesTable.programmeStart])] = programme.copy(
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

    fun getProgrammesForChannelId(
        channelId: UInt,
        count: Int = 4,
        now: Instant = Clock.System.now(),
    ): List<XmltvProgramme> = transaction {
        val query = EpgProgrammeTable
            .join(
                ChannelTable,
                JoinType.INNER,
                onColumn = EpgProgrammeTable.epgChannelId,
                otherColumn = ChannelTable.epgChannelId,
            )
            .selectAll()
            .andWhere { ChannelTable.id eq channelId }
            .andWhere {
                (EpgProgrammeTable.start greaterEq now) or (
                    (EpgProgrammeTable.start lessEq now) and (EpgProgrammeTable.stop greaterEq now)
                )
            }
            .orderBy(EpgProgrammeTable.start to SortOrder.ASC)
        if (count < Int.MAX_VALUE) query.limit(count)

        return@transaction query.map { it.toXmltvProgramme() }
    }

    fun getEpgChannelCount(): Long = transaction {
        EpgChannelTable.selectAll().count()
    }
    fun getEpgProgrammeCount(): Long = transaction {
        EpgProgrammeTable.selectAll().count()
    }

    fun cleanup() {
        transaction {
            EpgChannelTable.deleteWhere {
                EpgChannelTable.server notInList config.servers.map { it.name }
            }
            EpgProgrammeTable.deleteWhere {
                EpgProgrammeTable.server notInList config.servers.map { it.name }
            }
            for (server in config.servers) {
                EpgProgrammeTable.deleteWhere {
                    (EpgProgrammeTable.stop less Clock.System.now().minus(server.epgBefore))
                        .or(EpgProgrammeTable.stop lessEq Clock.System.now().minus(server.epgAfter))
                }
            }
            XmltvSourceTable.deleteWhere {
                XmltvSourceTable.server notInList config.servers.map { it.name }
            }

            EpgChannelTable.deleteWhere {
                EpgChannelTable.updatedAt less (Clock.System.now() - config.staleChannelTtl)
            }
            EpgProgrammeTable.deleteWhere {
                EpgProgrammeTable.updatedAt less (Clock.System.now() - config.staleChannelTtl)
            }
        }
    }

    companion object {
        private fun ResultRow.toXmltvDoc() = XmltvDoc(
            this[XmltvSourceTable.generatorInfoName],
            this[XmltvSourceTable.generatorInfoUrl],
            this[XmltvSourceTable.sourceInfoUrl],
            this[XmltvSourceTable.sourceInfoName],
            this[XmltvSourceTable.sourceInfoLogo],
        )
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
            server = this[EpgProgrammeTable.server],
        )
    }
}

package io.github.firstred.iptvproxy.db.repositories

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.tables.IptvChannelTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgChannelDisplayNameTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgChannelTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeAudioTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeCategoryTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeEpisodeNumberTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammePreviouslyShownTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeRatingTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeTable
import io.github.firstred.iptvproxy.db.tables.sources.PlaylistSourceTable
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
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvText
import io.github.firstred.iptvproxy.plugins.withForeignKeyChecksDisabled
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

class EpgRepository {
    fun upsertXmltvSourceForServer(doc: XmltvDoc, server: String) {
        transaction { withForeignKeyChecksDisabled {
            // Upsert the XMLTV source
            XmltvSourceTable.upsert {
                it[XmltvSourceTable.server] = server
                it[XmltvSourceTable.generatorInfoName] = doc.generatorInfoName
                it[XmltvSourceTable.generatorInfoUrl] = doc.generatorInfoUrl
                it[XmltvSourceTable.sourceInfoUrl] = doc.sourceInfoUrl
                it[XmltvSourceTable.sourceInfoName] = doc.sourceInfoName
                it[XmltvSourceTable.sourceInfoLogo] = doc.sourceInfoLogo
            } }
        }

        // Upsert the XMLTV channels
        doc.channels?.let { upsertXmltvChannelsForServer(it, server) }

        // Upsert the XMLTV programmes
        doc.programmes?.let { upsertXmltvProgrammesForServer(it, server) }
    }

    fun signalXmltvStartedForServer(server: String) {
        transaction { withForeignKeyChecksDisabled {
            XmltvSourceTable.upsert {
                it[XmltvSourceTable.server] = server
                it[XmltvSourceTable.startedAt] = Clock.System.now()
            } }
        }
    }
    fun signalXmltvCompletedForServer(server: String) {
        transaction { withForeignKeyChecksDisabled {
            XmltvSourceTable.upsert {
                it[XmltvSourceTable.server] = server
                it[XmltvSourceTable.completedAt] = Clock.System.now()
            } }
        }
    }

    fun upsertXmltvChannelsForServer(channels: List<XmltvChannel>, server: String) {
        channels.chunked(config.database.chunkSize).forEach { chunk ->
            chunk.forEach { channel ->
                channel.id?.let { channelId ->
                    transaction { withForeignKeyChecksDisabled {
                        EpgChannelTable.upsert {
                            it[EpgChannelTable.server] = server
                            it[EpgChannelTable.epgChannelId] = channelId
                            it[EpgChannelTable.icon] = channel.icon?.src
                            it[EpgChannelTable.name] = channel.displayNames?.firstOrNull { null == it.language }?.text ?: channel.displayNames?.firstOrNull()?.text ?: ""
                            it[EpgChannelTable.updatedAt] = Clock.System.now()
                        }
                    } }

                    channel.displayNames?.let { displayNames ->
                        transaction { withForeignKeyChecksDisabled {
                            EpgChannelDisplayNameTable.batchUpsert(displayNames) { displayName ->
                                this[EpgChannelDisplayNameTable.server] = server
                                this[EpgChannelDisplayNameTable.epgChannelId] = channelId
                                this[EpgChannelDisplayNameTable.language] = displayName.language ?: ""
                                this[EpgChannelDisplayNameTable.name] = displayName.text ?: ""
                            }
                        } }
                    }
                }
            }
        }
    }

    fun upsertXmltvProgrammesForServer(programmes: List<XmltvProgramme>, server: String) {
        programmes.chunked(config.database.chunkSize).forEach { chunk ->
            chunk.forEach { programme ->
                transaction { withForeignKeyChecksDisabled {
                    // Upsert the XMLTV programme
                    var programmeId: EntityID<Long>? = null
                    transaction {
                        programmeId = EpgProgrammeTable.insertIgnoreAndGetId {
                            it[EpgProgrammeTable.server] = server
                            it[EpgProgrammeTable.start] = programme.start
                            it[EpgProgrammeTable.stop] = programme.stop
                            it[EpgProgrammeTable.epgChannelId] = programme.channel
                            it[EpgProgrammeTable.title] = programme.title?.text ?: ""
                            it[EpgProgrammeTable.subtitle] = programme.subTitle?.text ?: ""
                            it[EpgProgrammeTable.description] = programme.desc?.text ?: ""
                            it[EpgProgrammeTable.icon] = programme.icon?.src
                            it[EpgProgrammeTable.updatedAt] = Clock.System.now()
                        }
                    }

                    programmeId?.value?.let { programmeId ->
                        // Upsert the categories
                        programme.category?.let { categories ->
                            EpgProgrammeCategoryTable.batchUpsert(categories) { category ->
                                this[EpgProgrammeCategoryTable.programmeId] = programmeId
                                this[EpgProgrammeCategoryTable.server] = server
                                this[EpgProgrammeCategoryTable.category] = category.text ?: ""
                                this[EpgProgrammeCategoryTable.language] = category.language ?: ""
                            }
                        }

                        // Upsert the episode numbers
                        programme.episodeNumbers?.let { episodeNumbers ->
                            episodeNumbers.filter { null != it.value }.let { episodeNumbers ->
                                EpgProgrammeEpisodeNumberTable.batchUpsert(episodeNumbers) { episodeNumber ->
                                    this[EpgProgrammeEpisodeNumberTable.server] = server
                                    this[EpgProgrammeEpisodeNumberTable.programmeId] = programmeId
                                    this[EpgProgrammeEpisodeNumberTable.system] = episodeNumber.system
                                    this[EpgProgrammeEpisodeNumberTable.number] = episodeNumber.value!!
                                }
                            }
                        }

                        // Upsert the ratings
                        programme.rating?.let { ratings -> ratings.filter { null != it.value }.forEach { rating ->
                                EpgProgrammeRatingTable.insertIgnore {
                                    it[EpgProgrammeRatingTable.server] = server
                                    it[EpgProgrammeRatingTable.programmeId] = programmeId
                                    it[EpgProgrammeRatingTable.system] = rating.system
                                    it[EpgProgrammeRatingTable.rating] = rating.value!!
                                }
                            }
                        }

                        // Upsert the previously shown
                        programme.previouslyShown?.let { it.filter{ null != it.start }.forEach { previouslyShown ->
                            EpgProgrammePreviouslyShownTable.insertIgnore {
                                it[EpgProgrammePreviouslyShownTable.server] = server
                                it[EpgProgrammePreviouslyShownTable.programmeId] = programmeId
                                it[EpgProgrammePreviouslyShownTable.start] = previouslyShown.start!!
                            }
                        } }

                        // Upsert the audio info
                        programme.audio?.let { audio ->
                            EpgProgrammeAudioTable.batchUpsert(audio.stereo) { stereo ->
                                this[EpgProgrammeAudioTable.server] = server
                                this[EpgProgrammeAudioTable.programmeId] = programmeId
                                this[EpgProgrammeAudioTable.type] = "stereo"
                                this[EpgProgrammeAudioTable.value] = stereo.value
                            }
                        }
                    }
                } }
            }
        }
    }

    fun forEachEpgChannelChunk(
        server: String? = null,
        chunkSize: Int = config.database.chunkSize,
        sortedByName: Boolean = false,
        action: (List<XmltvChannel>) -> Unit,
    ) {
        var offset = 0L

        do {
            val channelQuery = EpgChannelTable.selectAll()
            server?.let { channelQuery.where { EpgChannelTable.server eq it } }
            if (sortedByName) channelQuery.orderBy(EpgChannelTable.name)
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
        chunkSize: Int = config.database.chunkSize,
        action: (List<XmltvProgramme>) -> Unit,
    ) {
        var offset = 0L

        do {
            val programmeQuery = EpgProgrammeTable.selectAll()
            server?.let { programmeQuery.where { EpgProgrammeTable.server eq it } }
            programmeQuery
                .limit(chunkSize)
                .offset(offset)
            val programmes: MutableMap<String, XmltvProgramme> = transaction {
                programmeQuery
                    .associateBy(
                        { it[EpgProgrammeTable.id].toString() },
                        { it.toXmltvProgramme() },
                    )
                    .toMutableMap()
            }

            if (programmes.isEmpty()) break

            // Programme category
            transaction {
                val programmeCategoryTableQuery = EpgProgrammeCategoryTable
                    .selectAll()
                    .where { EpgProgrammeCategoryTable.programmeId inList programmes.keys.map { it.toLong() } }
                programmeCategoryTableQuery.forEach { row ->
                    programmes[row[EpgProgrammeCategoryTable.programmeId].toString()]?.let { programme ->
                        programmes[row[EpgProgrammeCategoryTable.programmeId].toString()] = programme.copy(
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
                val programmeAudioTableQuery = EpgProgrammeAudioTable
                    .selectAll()
                    .where { EpgProgrammeAudioTable.programmeId inList programmes.keys.map { it.toLong() } }
                programmeAudioTableQuery.forEach { row ->
                    programmes[row[EpgProgrammeAudioTable.programmeId].toString()]?.let { programme ->
                        programmes[row[EpgProgrammeAudioTable.programmeId].toString()] = programme.copy(
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
                val programmeEpisodeNumberTableQuery = EpgProgrammeEpisodeNumberTable
                    .selectAll()
                    .where { EpgProgrammeEpisodeNumberTable.programmeId inList programmes.keys.map { it.toLong() } }
                programmeEpisodeNumberTableQuery.forEach { row ->
                    programmes[row[EpgProgrammeEpisodeNumberTable.programmeId].toString()]?.let { programme ->
                        programmes[row[EpgProgrammeEpisodeNumberTable.programmeId].toString()] = programme.copy(
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
                val programmePreviouslyShownTableQuery = EpgProgrammePreviouslyShownTable
                    .selectAll()
                    .where { EpgProgrammePreviouslyShownTable.programmeId inList programmes.keys.map { it.toLong() } }
                programmePreviouslyShownTableQuery.forEach { row ->
                    programmes[row[EpgProgrammePreviouslyShownTable.programmeId].toString()]?.let { programme ->
                        programmes[row[EpgProgrammePreviouslyShownTable.programmeId].toString()] = programme.copy(
                            previouslyShown = (programme.previouslyShown ?: listOf()) + XmltvProgrammePreviouslyShown(
                                start = row[EpgProgrammePreviouslyShownTable.start],
                            )
                        )
                    }
                }
            }

            // Programme ratings
            transaction {
                val programmeRatingTableQuery = EpgProgrammeRatingTable
                    .selectAll()
                    .where { EpgProgrammeRatingTable.programmeId inList programmes.keys.map { it.toLong() } }
                programmeRatingTableQuery.forEach { row ->
                    programmes[row[EpgProgrammeRatingTable.programmeId].toString()]?.let { programme ->
                        programmes[row[EpgProgrammeRatingTable.programmeId].toString()] = programme.copy(
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
                    .selectAll()
                    .where { EpgProgrammeTable.id inList programmes.keys.map { it.toLong() } }
                programmeSubtitleTableQuery.forEach { row ->
                    programmes[row[EpgProgrammeTable.id].toString()]?.let { programme ->
                        programmes[row[EpgProgrammeTable.id].toString()] = programme.copy(
                            subTitle = XmltvText(
                                language = null,
                                text = row[EpgProgrammeTable.subtitle],
                            )
                        )
                    }
                }
            }

            action(programmes.values.toList())
            offset += chunkSize
        } while (programmes.isNotEmpty())
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

            for (server in config.servers.map { it.name }) {
                val startedAt = XmltvSourceTable
                    .select(XmltvSourceTable.server eq server)
                    .map { it[XmltvSourceTable.startedAt] }
                    .firstOrNull()
                if (null == startedAt) continue

                EpgChannelTable.deleteWhere {
                    EpgChannelTable.server eq server and
                            (EpgChannelTable.updatedAt less startedAt)
                }
                EpgProgrammeTable.deleteWhere {
                    EpgProgrammeTable.server eq server and
                            (EpgProgrammeTable.updatedAt less startedAt)
                }
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
        )
    }
}

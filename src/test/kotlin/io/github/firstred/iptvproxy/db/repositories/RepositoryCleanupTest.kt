package io.github.firstred.iptvproxy.db.repositories

import io.github.cdimascio.dotenv.dotenv
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.dotenv
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvText
import io.github.firstred.iptvproxy.loadConfig
import io.github.firstred.iptvproxy.testConfig
import io.github.firstred.iptvproxy.db.tables.epg.EpgChannelTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeTable
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.sqlite.SQLiteDataSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class RepositoryCleanupTest {
    @Test
    fun `refreshed epg channels and programmes survive cleanup while stale channels are removed`() {
        val repository = EpgRepository()
        val now = Clock.System.now()
        val staleChannelId = "stale-channel"

        repository.upsertXmltvChannels(
            listOf(
                XmltvChannel(
                    id = "fresh-channel",
                    displayNames = listOf(XmltvText(text = "Fresh channel")),
                ),
            ),
        )
        repository.upsertXmltvProgrammes(
            listOf(
                XmltvProgramme(
                    start = now - 1.minutes,
                    stop = now + 1.minutes,
                    channel = "fresh-channel",
                    title = XmltvText(text = "Fresh programme"),
                ),
            ),
        )
        sqliteDataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO epg_channel (epg_channel_id, icon, name, updated_at) VALUES (?, NULL, ?, ?)",
            ).use { statement ->
                statement.setString(1, staleChannelId)
                statement.setString(2, "Stale channel")
                statement.setLong(3, (now - config.channelMaxStalePeriod - 1.days).toEpochMilliseconds())
                statement.executeUpdate()
            }
        }

        repository.cleanup()

        val refreshedUpdatedAtType = sqliteDataSource.connection.use { connection ->
            connection.prepareStatement("SELECT typeof(updated_at) FROM epg_channel WHERE epg_channel_id = ?").use { statement ->
                statement.setString(1, "fresh-channel")
                statement.executeQuery().use { resultSet ->
                    assertEquals(true, resultSet.next())
                    resultSet.getString(1)
                }
            }
        }
        assertEquals("text", refreshedUpdatedAtType)

        val epgChannelCount = transaction {
            EpgChannelTable.selectAll().count()
        }
        assertEquals(1, epgChannelCount)
        val programme = transaction {
            EpgProgrammeTable.selectAll().singleOrNull()
        }
        assertNotNull(programme)
        assertEquals("Fresh programme", programme[EpgProgrammeTable.title])
    }

    @Test
    fun `live streams join channels from the same server`() {
        sqliteDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO live_stream (server, name, external_stream_id) VALUES " +
                        "('server-a', 'Stream A', 7), ('server-b', 'Stream B', 7)",
                )
                statement.executeUpdate(
                    "INSERT INTO channel (external_position, url, xtream_stream_id, server, name, main_group, groups, m3u_props, vlc_opts, type) VALUES " +
                        "(0, 'http://server-a/7.ts', 7, 'server-a', 'Channel A', '', '[]', '{}', '{}', 'live'), " +
                        "(0, 'http://server-b/7.ts', 7, 'server-b', 'Channel B', '', '[]', '{}', '{}', 'live')",
                )
            }
        }

        val streams = mutableListOf<io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStream>()
        XtreamRepository().forEachLiveStreamChunk(chunkSize = 10) { streams.addAll(it) }

        assertEquals(
            mapOf("server-a" to "http://server-a/7.ts", "server-b" to "http://server-b/7.ts"),
            streams.associate { it.server!! to it.url!! },
        )
    }

    companion object {
        private lateinit var databaseFile: Path
        private lateinit var sqliteDataSource: SQLiteDataSource

        @BeforeAll
        @JvmStatic
        fun setup() {
            dotenv = dotenv { ignoreIfMissing = true }
            config = loadConfig(testConfig)

            databaseFile = Files.createTempFile("iptv-proxy-repository-test", ".db")
            sqliteDataSource = SQLiteDataSource().apply {
                url = "jdbc:sqlite:${databaseFile.toAbsolutePath()}"
            }
            Flyway.configure()
                .locations("classpath:db/migrations/sqlite")
                .dataSource(sqliteDataSource)
                .placeholderReplacement(false)
                .load()
                .migrate()
            Database.connect(datasource = sqliteDataSource)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            Files.deleteIfExists(databaseFile)
        }
    }
}
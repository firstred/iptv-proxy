package io.github.firstred.iptvproxy.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.firstred.iptvproxy.BuildConfig
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.tables.AppDataTable
import io.github.firstred.iptvproxy.db.tables.ChannelTable
import io.github.firstred.iptvproxy.db.tables.channels.CategoryTable
import io.github.firstred.iptvproxy.db.tables.channels.LiveStreamTable
import io.github.firstred.iptvproxy.db.tables.channels.LiveStreamToCategoryTable
import io.github.firstred.iptvproxy.db.tables.channels.MovieTable
import io.github.firstred.iptvproxy.db.tables.channels.MovieToCategoryTable
import io.github.firstred.iptvproxy.db.tables.channels.SeriesTable
import io.github.firstred.iptvproxy.db.tables.channels.SeriesToCategoryTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgChannelDisplayNameTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgChannelTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeAudioTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeCategoryTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeEpisodeNumberTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammePreviouslyShownTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeRatingTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeSubtitlesTable
import io.github.firstred.iptvproxy.db.tables.epg.EpgProgrammeTable
import io.github.firstred.iptvproxy.db.tables.sources.PlaylistSourceTable
import io.github.firstred.iptvproxy.db.tables.sources.XmltvSourceTable
import io.github.firstred.iptvproxy.db.tables.sources.XtreamSourceTable
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnDatabaseInitializedHook
import io.github.firstred.iptvproxy.utils.dispatchHook
import io.github.z4kn4fein.semver.toVersion
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import org.slf4j.LoggerFactory

private val LOG = LoggerFactory.getLogger("DatabasePlugin")

val dataSource = HikariDataSource(HikariConfig().apply {
    jdbcUrl = config.database.jdbcUrl
    if (!config.database.username.isNullOrBlank()) username = config.database.username
    if (!config.database.password.isNullOrBlank()) password = config.database.password
    maximumPoolSize = config.database.maximumPoolSize.toInt()
    isAutoCommit = false
    isReadOnly = false
    transactionIsolation = config.database.transactionIsolation

    if (config.database.jdbcUrl.startsWith("jdbc:sqlite")) {
        val dataSourcePropertyKeys = config.database.dataSourceProperties.keys
        if (!dataSourcePropertyKeys.contains("synchronous")) {
            addDataSourceProperty("synchronous", "OFF")
        }
        if (!dataSourcePropertyKeys.contains("journal_mode")) {
            addDataSourceProperty("journal_mode", "MEMORY")
        }
        if (!dataSourcePropertyKeys.contains("cache_size")) {
            addDataSourceProperty("cache_size", "-256000")
        }
        if (!dataSourcePropertyKeys.contains("shared_cache")) {
            addDataSourceProperty("shared_cache", "true")
        }
    }

    config.database.dataSourceProperties.forEach { (key, value) ->
        addDataSourceProperty(key, value)
    }

    validate()
})

private val allDbTables = arrayOf(
    CategoryTable,
    LiveStreamTable,
    LiveStreamToCategoryTable,
    MovieTable,
    MovieToCategoryTable,
    SeriesTable,
    SeriesToCategoryTable,
    EpgChannelDisplayNameTable,
    EpgChannelTable,
    EpgProgrammeAudioTable,
    EpgProgrammeCategoryTable,
    EpgProgrammeEpisodeNumberTable,
    EpgProgrammePreviouslyShownTable,
    EpgProgrammeRatingTable,
    EpgProgrammeSubtitlesTable,
    EpgProgrammeTable,
    ChannelTable,
    PlaylistSourceTable,
    XmltvSourceTable,
    XtreamSourceTable,
    AppDataTable,
)

val appVersionsWithDestructiveMigrations = listOf("0.3.8".toVersion())

private fun Transaction.enableForeignKeyChecks() {
    when {
        config.database.jdbcUrl.startsWith("jdbc:sqlite") -> {
            exec(/** language=SQLite */ "PRAGMA foreign_keys = ON", explicitStatementType = StatementType.OTHER)
        }

        else -> {
            throw IllegalArgumentException("Unsupported database")
        }
    }
}

private fun Transaction.disableForeignKeyChecks() {
    when {
        config.database.jdbcUrl.startsWith("jdbc:sqlite") -> {
            exec(/** language=SQLite */ "PRAGMA foreign_keys = OFF", explicitStatementType = StatementType.OTHER)
        }

        else -> {
            throw IllegalArgumentException("Unsupported database type")
        }
    }
}

fun Transaction.withForeignKeyConstraintsDisabled(
    block: Transaction.() -> Unit
) {
    disableForeignKeyChecks()
    try {
        block()
    } finally {
        enableForeignKeyChecks()
    }
}

private fun dropAllTables() {
    val droppableTables = listOf(
        "live_stream_category",
        "live_stream",
        "live_stream_to_category",
        "movie_category",
        "movie",
        "movie_to_category",
        "series_category",
        "series",
        "series_to_category",
        "epg_channel_display_name",
        "epg_channel",
        "epg_programme_audio",
        "epg_programme_category",
        "epg_programme_episode_number",
        "epg_programme_previously_shown",
        "epg_programme_rating",
        "epg_programme_subtitles",
        "epg_programme",
    )

    transaction {
        withForeignKeyConstraintsDisabled {
            droppableTables.forEach { table ->
                try {
                    exec(/** language=SQL */ "DROP TABLE IF EXISTS $table", explicitStatementType = StatementType.DROP)
                } catch (e: Throwable) {
                    LOG.error("Error dropping table $table: ${e.message}")
                }
            }
        }
    }
}

private fun destructiveMigrationsRequired(): Boolean {
    val previousVersion = (transaction {
        try {
            AppDataTable.select(AppDataTable.value)
                .where { AppDataTable.name eq "current_version" }
                .map { it[AppDataTable.value] }
                .firstOrNull()
        } catch (_: Throwable) {
            null
        }
    } ?: "0.0.0").toVersion()

    return appVersionsWithDestructiveMigrations.any { previousVersion < it }
}

private fun checkAndRunDestructiveMigrations() {
    if (destructiveMigrationsRequired()) dropAllTables()
}

@OptIn(ExperimentalDatabaseMigrationApi::class)
@Suppress("UnusedReceiverParameter")
fun Application.configureDatabase() {
    val databaseSystem = when {
        config.database.jdbcUrl.startsWith("jdbc:sqlite") -> "sqlite"
        else -> throw IllegalArgumentException("Unsupported database")
    }

    Flyway.configure()
        .locations("classpath:db/migrations/$databaseSystem")
        .dataSource(dataSource)
        .load().migrate()

    Database.connect(dataSource)

    transaction {
        AppDataTable.upsert {
            it[name] = "current_version"
            it[value] = BuildConfig.APP_VERSION
        }
    }

    CoroutineScope(Job()).launch {
        delay(2_000L) // Delay dispatch of this hook - prevent too many db threads at once at start
        dispatchHook(HasApplicationOnDatabaseInitializedHook::class)
    }
}

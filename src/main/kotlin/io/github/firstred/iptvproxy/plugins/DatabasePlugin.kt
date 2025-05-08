package io.github.firstred.iptvproxy.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.firstred.iptvproxy.BuildConfig
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.tables.AppDataTable
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnDatabaseInitializedHook
import io.github.firstred.iptvproxy.utils.dispatchHook
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val LOG: Logger = LoggerFactory.getLogger("DatabasePlugin")

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

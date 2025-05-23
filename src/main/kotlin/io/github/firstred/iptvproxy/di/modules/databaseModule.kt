package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.db.repositories.ChannelRepository
import io.github.firstred.iptvproxy.db.repositories.EpgRepository
import io.github.firstred.iptvproxy.db.repositories.XtreamRepository
import io.github.firstred.iptvproxy.di.hooksOf
import io.github.firstred.iptvproxy.managers.DatabaseManager
import kotlinx.coroutines.sync.Mutex
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

val databaseModule = module {
    single { ChannelRepository() } binds hooksOf(ChannelRepository::class)
    single { EpgRepository() } binds hooksOf(EpgRepository::class)
    single { XtreamRepository() } binds hooksOf(XtreamRepository::class)
    single { DatabaseManager() } binds hooksOf(DatabaseManager::class)
    single(named("large-database-transactions")) { Mutex() }
}

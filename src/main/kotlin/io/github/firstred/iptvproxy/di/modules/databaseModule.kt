package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.db.repositories.ChannelRepository
import org.koin.dsl.module

val databaseModule = module {
    single { ChannelRepository() }
}

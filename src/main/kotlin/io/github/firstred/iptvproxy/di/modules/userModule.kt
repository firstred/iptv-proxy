package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.classes.IptvUser
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.di.hooksOf
import io.github.firstred.iptvproxy.managers.UserManager
import org.koin.dsl.binds
import org.koin.dsl.module

class IptvUsersByName : LinkedHashMap<String, IptvUser>()

val userModule = module {
    single<IptvUsersByName> {
        val users = IptvUsersByName()

        config.users.forEach {
            users[it.username] = IptvUser(it.username, it.password, it.maxConnections)
        }

        users
    }
    single { UserManager() } binds hooksOf(UserManager::class)
}

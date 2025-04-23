package io.github.firstred.iptvproxy.di

import io.github.firstred.iptvproxy.IptvServer
import io.github.firstred.iptvproxy.config
import org.koin.dsl.module

class IptvServersByName : LinkedHashMap<String, IptvServer>()

val iptvServerModule = module {
    single<IptvServersByName> {
        val servers = IptvServersByName()
        config.servers.forEach {
            val server = IptvServer(it.name, it, mutableListOf())
            servers[it.name] = server
        }
        servers
    }
}

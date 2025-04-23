package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.entities.IptvServer
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

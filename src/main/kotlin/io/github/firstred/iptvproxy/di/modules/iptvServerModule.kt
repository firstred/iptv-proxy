package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.classes.IptvServer
import io.github.firstred.iptvproxy.config
import org.koin.dsl.module

class IptvServersByName : LinkedHashMap<String, IptvServer>()

val iptvServerModule = module {
    single<IptvServersByName> {
        val servers = IptvServersByName()
        config.servers.forEach {
            servers[it.name] = it.toIptvServer()
        }
        servers
    }
}

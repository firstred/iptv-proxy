package io.github.firstred.iptvproxy.plugins

import io.github.firstred.iptvproxy.di.modules.IptvUsersByName
import io.github.firstred.iptvproxy.entities.IptvServerConnection
import io.github.firstred.iptvproxy.managers.UserManager
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext.get as getKoin

suspend fun RoutingContext.isAuthenticated(username: String?, token: String? = null, password: String? = null): Boolean {
    val userManager: UserManager by getKoin().inject()

    if (null == username) {
        call.respond(HttpStatusCode.Unauthorized, "Missing username")
        return false
    }

    if (token == null && password == null) {
        call.respond(HttpStatusCode.Unauthorized, "Missing token and/or password")
        return false
    }

    if (null != token) {
        if (null == UserManager.validateUserToken(username, token)) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid token: $token for username: $username")
            return false
        }
    } else if (!userManager.isAllowedUser(username, password!!)) {
        call.respond(HttpStatusCode.Unauthorized, "Invalid password: $password for username: $username")
        return false
    }

    return true
}
suspend fun RoutingContext.isNotAuthenticated(
    username: String?,
    token: String? = null,
    password: String? = null
): Boolean =
    !isAuthenticated(username, token, password)

suspend fun withUserPermit(
    username: String,
    action: suspend () -> Unit,
) {
    val iptvUsersByName: IptvUsersByName = getKoin().get()

    val semaphore = (iptvUsersByName[username] ?: throw IllegalArgumentException("User $username not found")).semaphore
    semaphore.acquire()
    try {
        action()
    } finally {
        semaphore.release()
    }
}

suspend fun withIptvServerConnection(
    channelId: String,
    action: suspend (connection: IptvServerConnection) -> Unit,
) {
    TODO()
//    val playlist = config.servers.find { it.name == server } ?: throw IllegalArgumentException("Server $server not found")
//    var idxConnection: Int? = null
//    lateinit var activeSemaphore: Semaphore
//    while (null === idxConnection) {
//        playlist.accounts?.forEachIndexed { idx, _ ->
//            val semaphore = getKoin().get<Semaphore>(named("iptv-server-$playlist-$idx"))
//            if (semaphore.tryAcquire()) {
//                idxConnection = idx
//                activeSemaphore = semaphore
//                return@forEachIndexed
//            }
//        }
//        delay(100L)
//    }
//
//    try {
//        action(server, idxConnection!!)
//    } finally {
//        activeSemaphore.release()
//    }
}


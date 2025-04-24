package io.github.firstred.iptvproxy.plugins

import io.github.firstred.iptvproxy.di.modules.IptvUsersByName
import io.github.firstred.iptvproxy.entities.IptvServerConnection
import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.managers.UserManager
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext.get as getKoin

suspend fun RoutingContext.isAuthenticated(username: String?, password: String? = null): Boolean {
    val userManager: UserManager by getKoin().inject()

    if (null == username) {
        call.respond(HttpStatusCode.Unauthorized, "Missing username")
        return false
    }

    if (!userManager.isAllowedUser(username, password!!)) {
        call.respond(HttpStatusCode.Unauthorized, "Invalid password: $password for username: $username")
        return false
    }

    return true
}
suspend fun RoutingContext.isNotAuthenticated(
    username: String?,
    password: String? = null
): Boolean = !isAuthenticated(username, password)

suspend fun withUserPermit(
    user: String,
    action: suspend () -> Unit,
) = withUserPermit(getKoin().get<IptvUsersByName>()[user] ?: throw IllegalArgumentException("User $user not found"), action)
suspend fun withUserPermit(
    user: IptvUser,
    action: suspend () -> Unit,
) {
    val semaphore = user.semaphore
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


package io.github.firstred.iptvproxy

import io.ktor.server.routing.*
import org.koin.core.component.KoinComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IptvProxyService : KoinComponent {
    fun handleLive(call: RoutingCall) {
        TODO()
//        val ch = call.parameters["id"]
//        val channel = channels[ch]
//        if (channel == null) {
//            LOG.warn("channel not found: {}, for request: {}", ch, call.request.path())
//            return
//        }
//
//        // we need user if this is not m3u request
//        val token = call.parameters["token"]
//        val username = call.parameters["username"]
//        var user = UserManager.validateUserToken(username.toString(), token)
//
//        // pass user name from another iptv-proxy
//        val proxyUser: String? = call.request.headers[IptvServer.PROXY_USER_HEADER]
//
//        // no token, or user is not verified
//        if (user == null) {
//            LOG.warn("invalid user token: {}, proxyUser: {}", token, proxyUser)
//            return
//        }
//
//        if (proxyUser != null) user = "$user:$proxyUser"
//
//        val iu = iptvUsers.computeIfAbsent(user) {
//            UserSemaphores(it) { key: String, value: UserSemaphores -> iptvUsers.remove(key, value) }
//        }
////        iu.lock()
////        try {
////            val serverConnection = iu.getServerConnection(channel) ?: return false
////
////            return serverConnection.handle(call, path, iu, token)
////        } finally {
////            iu.unlock()
////        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IptvProxyService::class.java)
    }
}

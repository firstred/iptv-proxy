package io.github.firstred.iptvproxy

import kotlinx.coroutines.sync.Semaphore

class IptvUser(
    val username: String,
    val password: String,
    maxConnections: Int,
) {
    val semaphore = Semaphore(maxConnections)
}

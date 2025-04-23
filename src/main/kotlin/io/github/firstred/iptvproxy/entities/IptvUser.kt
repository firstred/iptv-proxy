package io.github.firstred.iptvproxy.entities

import io.github.firstred.iptvproxy.serialization.serializers.IntWithUnderscoreSerializer
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.Serializable

@Serializable
class IptvUser(
    val username: String,
    val password: String,
    @Serializable(with = IntWithUnderscoreSerializer::class) val maxConnections: Int,
) {
    val semaphore = Semaphore(maxConnections)
}

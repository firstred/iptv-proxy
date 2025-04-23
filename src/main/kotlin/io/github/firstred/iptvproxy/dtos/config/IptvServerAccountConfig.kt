package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.serialization.serializers.IntWithUnderscoreSerializer
import io.github.firstred.iptvproxy.utils.defaultMaxConnections
import kotlinx.serialization.Serializable

@Serializable
data class IptvServerAccountConfig(
    var idx: Int = -1,
    val url: String? = null,
    val login: String? = null,
    val password: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    @Serializable(with = IntWithUnderscoreSerializer::class) val maxConcurrentRequests: Int = defaultMaxConnections,
    @Serializable(with = IntWithUnderscoreSerializer::class) val maxConcurrentRequestsPerHost: Int = defaultMaxConnections,
    @Serializable(with = IntWithUnderscoreSerializer::class) val maxConcurrentRequestsPerChannel: Int = defaultMaxConnections, // Unused -- reserved for future use
)

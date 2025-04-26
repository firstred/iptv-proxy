package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamUserInfo(
    val username: String,
    val password: String,
    val message: String? = null,
    val auth: Int? = null,
    val status: String? = null,

    @SerialName("exp_date") val expirationDate: Instant? = null,
    @SerialName("is_trial") val isTrial: String? = null,
    @SerialName("active_cons") val activeConnections: String? = null,
    @SerialName("created_at") val createdAt: Instant? = null,
    @SerialName("max_connections") val maxConnections: String? = null,
    @SerialName("allowed_output_formats") val allowedOutputFormats: List<XtreamOutputFormat>,
)

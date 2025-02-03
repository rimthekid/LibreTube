package com.github.libretube.api.obj

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class PipedInstance(
    val name: String,
    @SerialName("api_url") val apiUrl: String,
    val locations: String = "",
    val version: String = "",
    @SerialName("up_to_date") val upToDate: Boolean = true,
    val cdn: Boolean = false,
    val registered: Long = 0,
    @SerialName("last_checked") val lastChecked: Long = 0,
    @SerialName("cache") val cache: Boolean = false,
    @SerialName("s3_enabled") val s3Enabled: Boolean = false,
    @SerialName("image_proxy_url") val imageProxyUrl: String = "",
    @SerialName("registration_disabled") val registrationDisabled: Boolean = false,
    @SerialName("uptime_24h") val uptimeToday: Float? = null,
    @SerialName("uptime_7d") val uptimeWeek: Float? = null,
    @SerialName("uptime_30d") val uptimeMonth: Float? = null,
    val isCurrentlyDown: Boolean = false
) : Parcelable

package com.github.libretube.api.obj

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class Playlists(
    val id: String? = null,
    var name: String? = null,
    var shortDescription: String? = null,
    val thumbnail: String? = null,
    val videos: Long = 0
) : Parcelable

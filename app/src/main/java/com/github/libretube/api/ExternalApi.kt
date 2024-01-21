package com.github.libretube.api

import com.github.libretube.api.obj.PipedInstance
import com.github.libretube.api.obj.SubmitSegmentResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

private const val SB_API_URL = "https://sponsor.ajay.app"
private const val GITHUB_API_URL = "https://api.github.com/repos/libre-tube/LibreTube/releases/latest"

@Serializable
data class Release(
    val name: String, // version name
    val body: String, // changelog
    val html_url: String // uri to latest release tag
)

interface ExternalApi {
    // only for fetching servers list
    @GET
    suspend fun getInstances(@Url url: String): List<PipedInstance>

    // fetch latest version info
    @GET(GITHUB_API_URL)
    suspend fun getLatestRelease(): Response<Release>

    @POST("$SB_API_URL/api/skipSegments")
    suspend fun submitSegment(
        @Query("videoID") videoId: String,
        @Query("startTime") startTime: Float,
        @Query("endTime") endTime: Float,
        @Query("category") category: String,
        @Query("userAgent") userAgent: String,
        @Query("userID") userID: String,
        @Query("duration") duration: Float? = null,
        @Query("description") description: String = ""
    ): List<SubmitSegmentResponse>

    /**
     * @param score: 0 for downvote, 1 for upvote, 20 for undoing previous vote (if existent)
     */
    @POST("$SB_API_URL/api/voteOnSponsorTime")
    suspend fun voteOnSponsorTime(
        @Query("UUID") uuid: String,
        @Query("userID") userID: String,
        @Query("type") score: Int
    )
}

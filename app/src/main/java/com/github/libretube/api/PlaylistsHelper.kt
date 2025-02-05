package com.github.libretube.api

import androidx.core.text.isDigitsOnly
import com.github.libretube.api.obj.Playlist
import com.github.libretube.api.obj.Playlists
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.enums.PlaylistType
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.obj.PipedImportPlaylist
import com.github.libretube.repo.LocalPlaylistsRepository
import com.github.libretube.repo.PipedPlaylistRepository
import com.github.libretube.repo.PlaylistRepository
import com.github.libretube.util.deArrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object PlaylistsHelper {
    private val pipedPlaylistRegex =
        "[\\da-fA-F]{8}-[\\da-fA-F]{4}-[\\da-fA-F]{4}-[\\da-fA-F]{4}-[\\da-fA-F]{12}".toRegex()
    const val MAX_CONCURRENT_IMPORT_CALLS = 5

    private val token get() = PreferenceHelper.getToken()
    val loggedIn: Boolean get() = token.isNotEmpty()
    private val playlistsRepository: PlaylistRepository
        get() = when {
            loggedIn -> PipedPlaylistRepository()
            else -> LocalPlaylistsRepository()
        }

    suspend fun getPlaylists(): List<Playlists> = withContext(Dispatchers.IO) {
        val playlists = playlistsRepository.getPlaylists()
        sortPlaylists(playlists)
    }

    private fun sortPlaylists(playlists: List<Playlists>): List<Playlists> {
        return when (
            PreferenceHelper.getString(PreferenceKeys.PLAYLISTS_ORDER, "creation_date")
        ) {
            "creation_date" -> playlists
            "creation_date_reversed" -> playlists.reversed()
            "alphabetic" -> playlists.sortedBy { it.name?.lowercase() }
            "alphabetic_reversed" -> playlists.sortedBy { it.name?.lowercase() }
                .reversed()

            else -> playlists
        }
    }

    suspend fun getPlaylist(playlistId: String): Playlist {
        // load locally stored playlists with the auth api
        return when (getPrivatePlaylistType(playlistId)) {
            PlaylistType.PUBLIC -> RetrofitInstance.api.getPlaylist(playlistId)
            else -> playlistsRepository.getPlaylist(playlistId)
        }.apply {
            relatedStreams = relatedStreams.deArrow()
        }
    }

    suspend fun getAllPlaylistsWithVideos(playlistIds: List<String>? = null): List<Playlist> {
        return withContext(Dispatchers.IO) {
            (playlistIds ?: getPlaylists().map { it.id!! })
                .map { async { getPlaylist(it) } }
                .awaitAll()
        }
    }

    suspend fun createPlaylist(playlistName: String) =
        playlistsRepository.createPlaylist(playlistName)

    suspend fun addToPlaylist(playlistId: String, vararg videos: StreamItem) =
        withContext(Dispatchers.IO) {
            playlistsRepository.addToPlaylist(playlistId, *videos)
        }

    suspend fun renamePlaylist(playlistId: String, newName: String) =
        playlistsRepository.renamePlaylist(playlistId, newName)

    suspend fun changePlaylistDescription(playlistId: String, newDescription: String) =
        playlistsRepository.changePlaylistDescription(playlistId, newDescription)

    suspend fun removeFromPlaylist(playlistId: String, index: Int) =
        playlistsRepository.removeFromPlaylist(playlistId, index)

    suspend fun importPlaylists(playlists: List<PipedImportPlaylist>) =
        playlistsRepository.importPlaylists(playlists)

    suspend fun clonePlaylist(playlistId: String) = playlistsRepository.clonePlaylist(playlistId)
    suspend fun deletePlaylist(playlistId: String) = playlistsRepository.deletePlaylist(playlistId)

    fun getPrivatePlaylistType(): PlaylistType {
        return if (loggedIn) PlaylistType.PRIVATE else PlaylistType.LOCAL
    }

    private fun getPrivatePlaylistType(playlistId: String): PlaylistType {
        return if (playlistId.isDigitsOnly()) {
            PlaylistType.LOCAL
        } else if (playlistId.matches(pipedPlaylistRegex)) {
            PlaylistType.PRIVATE
        } else {
            PlaylistType.PUBLIC
        }
    }
}

package com.github.libretube.ui.models

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.libretube.R
import com.github.libretube.api.PlaylistsHelper
import com.github.libretube.api.obj.Playlists
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.constants.IntentData
import com.github.libretube.util.PlayingQueue
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

class PlaylistViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = savedStateHandle.getStateFlow(UI_STATE, UiState())
    val uiState = _uiState.asLiveData()

    init {
        fetchPlaylists()
    }

    fun fetchPlaylists() {
        viewModelScope.launch {
            kotlin.runCatching {
                PlaylistsHelper.getPlaylists()
            }.onSuccess { playlists ->
                savedStateHandle[UI_STATE] = _uiState.value.copy(
                    playlists = playlists.filterNot { list -> list.name.isNullOrEmpty() }
                )
            }.onFailure {
                savedStateHandle[UI_STATE] = _uiState.value.copy(
                    message = UiState.Message(R.string.unknown_error)
                )
            }
        }
    }

    fun onPostiveButtonClick(playlistIndex: Int) {
        val playlist = _uiState.value.playlists.getOrElse(playlistIndex) { return }
        savedStateHandle[UI_STATE] = _uiState.value.copy(lastSelectedPlaylistId = playlist.id)

        val videoInfo = savedStateHandle.get<StreamItem>(IntentData.videoInfo)
        val streams = videoInfo?.let { listOf(it) } ?: PlayingQueue.getStreams()

        viewModelScope.launch {
            kotlin.runCatching {
                if (streams.isEmpty()) {
                    throw IllegalArgumentException()
                }
                PlaylistsHelper.addToPlaylist(playlist.id!!, *streams.toTypedArray())
            }.onSuccess {
                savedStateHandle[UI_STATE] = _uiState.value.copy(
                    message = UiState.Message(R.string.added_to_playlist, listOf(playlist.name!!)),
                    saved = Unit,
                )
            }
            .onFailure {
                savedStateHandle[UI_STATE] = _uiState.value.copy(
                    message = UiState.Message(R.string.unknown_error)
                )
            }
        }
    }

    fun onMessageShown() {
        savedStateHandle[UI_STATE] = _uiState.value.copy(message = null)
    }

    fun onDismissed() {
        savedStateHandle[UI_STATE] = _uiState.value.copy(saved = null)
    }

    @Parcelize
    data class UiState(
        val lastSelectedPlaylistId: String? = null,
        val playlists: List<Playlists> = emptyList(),
        val message: Message? = null,
        val saved: Unit? = null,
    ) : Parcelable {
        @Parcelize
        data class Message(
            @StringRes val resId: Int,
            val formatArgs: List<@RawValue Any>? = null,
        ) : Parcelable
    }

    companion object {
        private const val UI_STATE = "ui_state"

        val Factory = viewModelFactory {
            initializer {
                PlaylistViewModel(
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}

package com.github.libretube.ui.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import com.github.libretube.R
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.constants.IntentData
import com.github.libretube.databinding.DialogAddToPlaylistBinding
import com.github.libretube.extensions.parcelable
import com.github.libretube.ui.models.PlaylistViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialog to insert new videos to a playlist
 * videoId: The id of the video to add. If non is provided, insert the whole playing queue
 */
class AddToPlaylistDialog : DialogFragment() {

    private var videoInfo: StreamItem? = null
    private val viewModel: PlaylistViewModel by viewModels { PlaylistViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoInfo = arguments?.parcelable(IntentData.videoInfo)
        Log.e("video info", videoInfo.toString())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        childFragmentManager.setFragmentResultListener(
            CreatePlaylistDialog.CREATE_PLAYLIST_DIALOG_REQUEST_KEY,
            this
        ) { _, resultBundle ->
            val addedToPlaylist = resultBundle.getBoolean(IntentData.playlistTask)
            if (addedToPlaylist) {
                viewModel.fetchPlaylists()
            }
        }

        val binding = DialogAddToPlaylistBinding.inflate(layoutInflater)
        viewModel.uiState.observe(this) { (lastSelectedPlaylistId, playlists, msg, saved) ->
            binding.playlistsSpinner.items = playlists.mapNotNull { it.name }

            // select the last used playlist
            lastSelectedPlaylistId?.let { id ->
                binding.playlistsSpinner.selectedItemPosition = playlists
                    .indexOfFirst { it.id == id }
                    .takeIf { it >= 0 } ?: 0
            }

            msg?.let {
                with(binding.root.context) {
                    Toast.makeText(this, getString(it.resId, it.formatArgs), Toast.LENGTH_SHORT).show()
                }
                viewModel.onMessageShown()
            }

            saved?.let {
                dismiss()
                viewModel.onDismissed()
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.addToPlaylist)
            .setNegativeButton(R.string.createPlaylist) { _, _ ->
                CreatePlaylistDialog().show(childFragmentManager, null)
            }
            .setPositiveButton(R.string.addToPlaylist) { _, _ ->
                val selectedItemPosition = binding.playlistsSpinner.selectedItemPosition
                viewModel.onPostiveButtonClick(selectedItemPosition)
            }
            .setView(binding.root)
            .show()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        setFragmentResult(ADD_TO_PLAYLIST_DIALOG_DISMISSED_KEY, bundleOf())
    }

    companion object {
        const val ADD_TO_PLAYLIST_DIALOG_DISMISSED_KEY = "add_to_playlist_dialog_dismissed"
    }
}

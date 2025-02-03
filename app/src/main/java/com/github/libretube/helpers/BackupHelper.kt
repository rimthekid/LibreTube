package com.github.libretube.helpers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.github.libretube.api.JsonHelper
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.db.DatabaseHolder.Database
import com.github.libretube.extensions.TAG
import com.github.libretube.obj.BackupFile
import com.github.libretube.obj.PreferenceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Backup and restore the preferences
 */
object BackupHelper {
    /**
     * Write a [BackupFile] containing the database content as well as the preferences
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun createAdvancedBackup(context: Context, uri: Uri, backupFile: BackupFile) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                JsonHelper.json.encodeToStream(backupFile, outputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG(), "Error while writing backup: $e")
        }
    }

    /**
     * Restore data from a [BackupFile]
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun restoreAdvancedBackup(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val backupFile = context.contentResolver.openInputStream(uri)?.use {
            JsonHelper.json.decodeFromStream<BackupFile>(it)
        } ?: return@withContext

        Database.watchHistoryDao().insertAll(backupFile.watchHistory.orEmpty())
        Database.searchHistoryDao().insertAll(backupFile.searchHistory.orEmpty())
        Database.watchPositionDao().insertAll(backupFile.watchPositions.orEmpty())
        Database.localSubscriptionDao().insertAll(backupFile.subscriptions.orEmpty())
        Database.customInstanceDao().insertAll(backupFile.customInstances.orEmpty())
        Database.playlistBookmarkDao().insertAll(backupFile.playlistBookmarks.orEmpty())
        Database.subscriptionGroupsDao().insertAll(backupFile.groups.orEmpty())

        backupFile.localPlaylists?.forEach {
            // the playlist will be created with an id of 0, so that Room will auto generate a
            // new playlist id to avoid conflicts with existing local playlists
            val playlistId = Database.localPlaylistsDao().createPlaylist(it.playlist.copy(id = 0))
            it.videos.forEach { playlistItem ->
                playlistItem.playlistId = playlistId.toInt()
                Database.localPlaylistsDao().addPlaylistVideo(playlistItem.copy(id = 0))
            }
        }

        restorePreferences(context, backupFile.preferences)
    }

    /**
     * Restore the shared preferences from a backup file
     */
    private fun restorePreferences(context: Context, preferences: List<PreferenceItem>?) {
        if (preferences == null) return

        PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) {
            // clear the previous settings
            clear()

            // decide for each preference which type it is and save it to the preferences
            preferences.forEach { (key, jsonValue) ->
                val value = if (jsonValue.isString) {
                    jsonValue.content
                } else {
                    jsonValue.booleanOrNull
                        ?: jsonValue.intOrNull
                        ?: jsonValue.longOrNull
                        ?: jsonValue.floatOrNull
                }
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    is Long -> putLong(key, value)
                    is Int -> {
                        // we only use integers for SponsorBlock colors and the start fragment
                        if (key == PreferenceKeys.START_FRAGMENT || "_color" in key.orEmpty()) {
                            putInt(key, value)
                        } else {
                            putLong(key, value.toLong())
                        }
                    }

                    is String -> {
                        if (
                            key == PreferenceKeys.HOME_TAB_CONTENT ||
                            key == PreferenceKeys.SELECTED_FEED_FILTERS
                        ) {
                            putStringSet(key, value.split(",").toSet())
                        } else {
                            putString(key, value)
                        }
                    }
                }
            }
        }

        // re-schedule the notification worker as some settings related to it might have changed
        NotificationHelper.enqueueWork(context, ExistingPeriodicWorkPolicy.UPDATE)
    }
}

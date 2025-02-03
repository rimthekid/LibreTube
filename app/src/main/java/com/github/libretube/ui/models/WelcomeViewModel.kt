package com.github.libretube.ui.models

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.asLiveData
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.libretube.R
import com.github.libretube.api.InstanceRepository
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.api.obj.PipedInstance
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.helpers.BackupHelper
import com.github.libretube.helpers.PreferenceHelper
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class WelcomeViewModel(
    private val instanceRepository: InstanceRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = savedStateHandle.getStateFlow(UI_STATE, UiState())
    val uiState = _uiState.asLiveData()

    init {
        viewModelScope.launch {
            instanceRepository.getInstances()
                .onSuccess { instances ->
                    savedStateHandle[UI_STATE] = _uiState.value.copy(instances = instances)
                }
                .onFailure {
                    savedStateHandle[UI_STATE] = _uiState.value.copy(
                        instances = instanceRepository.getInstancesFallback(),
                        error = R.string.failed_fetching_instances,
                    )
                }
        }
    }

    fun setSelectedInstanceIndex(index: Int) {
        savedStateHandle[UI_STATE] = _uiState.value.copy(selectedInstanceIndex = index)
    }

    fun saveSelectedInstance() {
        val selectedInstanceIndex = _uiState.value.selectedInstanceIndex
        if (selectedInstanceIndex == null) {
            savedStateHandle[UI_STATE] = _uiState.value.copy(error = R.string.choose_instance)
        } else {
            PreferenceHelper.putString(
                PreferenceKeys.FETCH_INSTANCE,
                _uiState.value.instances[selectedInstanceIndex].apiUrl
            )
            refreshAndNavigate()
        }
    }

    fun restoreAdvancedBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            BackupHelper.restoreAdvancedBackup(context, uri)

            // only skip the welcome activity if the restored backup contains an instance
            val instancePref = PreferenceHelper.getString(PreferenceKeys.FETCH_INSTANCE, "")
            if (instancePref.isNotEmpty()) {
                refreshAndNavigate()
            }
        }
    }

    private fun refreshAndNavigate() {
        // refresh the api urls since they have changed likely
        RetrofitInstance.lazyMgr.reset()
        savedStateHandle[UI_STATE] = _uiState.value.copy(navigateToMain = Unit)
    }

    fun onErrorShown() {
        savedStateHandle[UI_STATE] = _uiState.value.copy(error = null)
    }

    fun onNavigated() {
        savedStateHandle[UI_STATE] = _uiState.value.copy(navigateToMain = null)
    }

    @Parcelize
    data class UiState(
        val selectedInstanceIndex: Int? = null,
        val instances: List<PipedInstance> = emptyList(),
        @StringRes val error: Int? = null,
        val navigateToMain: Unit? = null,
    ) : Parcelable

    companion object {
        private const val UI_STATE = "ui_state"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WelcomeViewModel(
                    instanceRepository = InstanceRepository(this[APPLICATION_KEY]!!),
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}

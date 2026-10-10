package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.update.AppRelease
import io.github.yulbax.erkin.update.UpdateChecker
import io.github.yulbax.erkin.update.UpdateDownloader
import io.github.yulbax.erkin.update.UpdatePlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Hidden : UpdateUiState

    data class Available(val release: AppRelease, val current: String, val installable: Boolean) : UpdateUiState

    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateUiState

    data class Failed(val release: AppRelease) : UpdateUiState
}

class UpdateViewModel(
    private val checker: UpdateChecker,
    private val downloader: UpdateDownloader,
    private val platform: UpdatePlatform
) : ViewModel() {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()
    private var download: Job? = null

    init {
        viewModelScope.launch {
            val release = try {
                checker.check()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            } ?: return@launch
            _state.value = UpdateUiState.Available(
                release = release,
                current = checker.currentVersion.toString(),
                installable = platform.assetSuffix?.let(release::asset) != null
            )
        }
    }

    fun update() {
        val available = _state.value as? UpdateUiState.Available ?: return
        val release = available.release
        val asset = platform.assetSuffix?.let(release::asset) ?: return
        _state.value = UpdateUiState.Downloading(release, progress = null)
        download = viewModelScope.launch {
            try {
                val file = downloader.download(asset, platform.downloadDir) { progress ->
                    _state.value = UpdateUiState.Downloading(release, progress)
                }
                _state.value = UpdateUiState.Hidden
                platform.install(file)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = UpdateUiState.Failed(release)
            }
        }
    }

    fun dismiss() {
        download?.cancel()
        download = null
        _state.value = UpdateUiState.Hidden
    }
}

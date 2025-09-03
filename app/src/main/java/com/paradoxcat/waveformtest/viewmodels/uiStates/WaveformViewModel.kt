package com.paradoxcat.waveformtest.viewmodels.uiStates

import android.net.Uri
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paradoxcat.waveformtest.repositories.AudioRepository
import com.paradoxcat.waveformtest.services.AudioPlayerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WaveformViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val audioPlayer: AudioPlayerService

) : ViewModel() {
    private val _uiState = MutableLiveData(WaveformUiState())
    val uiState: LiveData<WaveformUiState> = _uiState


    private val _debugInfo = MutableLiveData<String>()
    val debugInfo: LiveData<String> = _debugInfo


    private var currentAudioUri: Uri? = null


    init {
        setupAudioPlayerCallbacks()
    }

    private fun setupAudioPlayerCallbacks() {
        audioPlayer.setOnPositionUpdateListener { position ->
            val currentState = _uiState.value ?: return@setOnPositionUpdateListener
            _uiState.value = currentState.copy(currentPosition = position)
        }

        audioPlayer.setOnCompletionListener {
            val currentState = uiState.value ?: return@setOnCompletionListener
            _uiState.value = currentState.copy(
                isPlaying = false,
                currentPosition = 0L
            )
        }
    }


    fun loadAudioFile(uri: Uri) {
        currentAudioUri = uri
        _uiState.value = WaveformUiState(isLoading = true)

        viewModelScope.launch {

            try {
                _debugInfo.value = "Starting to upload the file"

                val result = audioRepository.loadAudioFile(uri)

                if (result.isSuccess) {
                    val (audioData, fileName) = result.getOrThrow()

                    val duration = audioPlayer.prepareAudio(uri)

                    _debugInfo.value = "file was uploaded "


                    _uiState.value = WaveformUiState(
                        isLoading = false,
                        audioData = audioData,
                        fileName = fileName,
                        totalDuration = duration,
                        sampleRate = 44100
                    )
                } else {
                    handleError(result.exceptionOrNull()?.message ?: "Unknown Error")

                }
            } catch (e: Exception) {
                handleError(e.message.toString())
            }

        }


    }


    fun togglePlayPause() {
        val currentState = _uiState.value ?: return
        if (!currentState.hasData) return


        if (currentState.isPlaying) {
            audioPlayer.pause()
            _uiState.value = currentState.copy(isPlaying = false)
            _debugInfo.value = "Audio paused et ${formatTime(currentState.currentPosition)}"
        } else {
            audioPlayer.play()
            _uiState.value = currentState.copy(isPlaying = true)
            _debugInfo.value = "Audio stared from  ${formatTime(currentState.currentPosition)}"
        }

    }


    fun seekTo(position: Long) {
        val currentState = _uiState.value ?: return
        if (!currentState.hasData) return

        audioPlayer.seekTo(position)
        _uiState.value = currentState.copy(currentPosition = position)
        _debugInfo.value = "Skip  to ${formatTime(position)}"
    }

    fun seekToPercentage(percentage: Float) {
        val currentState = _uiState.value ?: return
        val targetPosition = (currentState.totalDuration * percentage).toLong()
        seekTo(targetPosition)
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    private fun handleError(message: String) {
        _debugInfo.value = "Error: $message"
        _uiState.value = WaveformUiState(
            isLoading = false,
            errorMessage = "Cannot upload file : $message"
        )
    }


    fun clearError() {
        val currentState = _uiState.value ?: return
        if (currentState.hasError) {
            _uiState.value = currentState.copy(errorMessage = null)

        }
    }

    fun clearData() {
        audioPlayer.release()
        _uiState.value = WaveformUiState()
        _debugInfo.value = "Data deleted"
    }
    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }

}
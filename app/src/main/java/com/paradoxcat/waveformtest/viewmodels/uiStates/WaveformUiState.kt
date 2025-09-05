package com.paradoxcat.waveformtest.viewmodels.uiStates

import android.util.Log
import java.nio.ByteBuffer


data class WaveformUiState(
    val isLoading :  Boolean = false,
    val audioData : ByteBuffer? = null,
    val errorMessage : String? = null,
    val fileName : String? = null,
    val currentPosition: Long = 0L,
    val isPlaying : Boolean = false,
    val totalDuration: Long = 0L,
    val sampleRate : Int = 44100


){
    val hasData: Boolean get() = audioData != null
    val hasError: Boolean get() = errorMessage != null
    val progressPercentage: Float get() = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f


    fun formatCurrentTime(): String = formatTime(currentPosition)
    fun formatTotalTime(): String = formatTime(totalDuration)

    private fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }



}
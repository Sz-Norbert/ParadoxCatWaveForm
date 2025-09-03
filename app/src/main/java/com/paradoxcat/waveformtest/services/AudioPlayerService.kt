package com.paradoxcat.waveformtest.services

import android.net.Uri

interface AudioPlayerService {

    fun setOnPositionUpdateListener(callback: (Long) -> Unit)
    fun setOnCompletionListener(callback: () -> Unit)
    suspend fun prepareAudio(uri: Uri): Long

    fun play()
    fun pause()
    fun seekTo(position: Long)
    fun getCurrentPosition(): Long
    fun isPlaying(): Boolean
    fun release()
}
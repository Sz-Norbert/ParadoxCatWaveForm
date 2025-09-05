package com.paradoxcat.waveformtest.services

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton

class AudioPlayerServiceImpl @Inject constructor (
    @ApplicationContext private val context: Context

) : AudioPlayerService {


    private var mediaPlayer: MediaPlayer? = null
    private var positionUpdateCallback: ((Long) -> Unit)? = null
    private var completionCallback: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var positionUpdateRunnable: Runnable? = null




    override fun setOnPositionUpdateListener(callback: (Long) -> Unit) {
        positionUpdateCallback = callback
    }

    override fun setOnCompletionListener(callback: () -> Unit) {

        completionCallback = callback
    }

    override suspend fun prepareAudio(uri: Uri): Long = withContext(Dispatchers.Main) {
        release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context,uri)
            prepare()
            setOnCompletionListener {
                stopPositionUpdates()
                completionCallback?.invoke()
            }
        }
        mediaPlayer?.duration?.toLong() ?: 0L

    }

    override fun play() {
        mediaPlayer?.start()
        startPositionUpdates()

    }

    override fun pause() {
        mediaPlayer?.pause()
        stopPositionUpdates()    }

    override fun seekTo(position: Long) {
        mediaPlayer?.seekTo(position.toInt())
    }

    override fun getCurrentPosition(): Long = mediaPlayer?.currentPosition?.toLong() ?: 0L

    override fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true


    override fun release() {
        stopPositionUpdates()
        mediaPlayer?.release()
        mediaPlayer = null    }


    private fun stopPositionUpdates() {
        positionUpdateRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun startPositionUpdates() {
        positionUpdateRunnable = object : Runnable {
            override fun run() {
                positionUpdateCallback?.invoke(getCurrentPosition())
                handler.postDelayed(this, 50)
            }
        }
        handler.post(positionUpdateRunnable!!)
    }
}
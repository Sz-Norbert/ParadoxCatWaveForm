package com.paradoxcat.waveformtest.repositories

import android.net.Uri
import java.nio.ByteBuffer

interface AudioRepository {

    suspend fun loadAudioFile(uri: Uri): Result<Pair<ByteBuffer, String>>

}
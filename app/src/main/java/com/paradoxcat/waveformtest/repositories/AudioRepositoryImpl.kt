package com.paradoxcat.waveformtest.repositories

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRepositoryImpl @Inject constructor (@ApplicationContext private val context : Context) : AudioRepository {


    override suspend fun loadAudioFile(uri: Uri): Result<Pair<ByteBuffer, String>> =
        withContext(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(Exception("cannot open the file"))

                val fileName = getFileName(contentResolver, uri)

                val audioData = inputStream.use { stream ->
                    val bytes = stream.readBytes()
                    val actualAudioData = if (bytes.size > 44 &&
                        String(bytes.sliceArray(0..3)) == "RIFF"
                    ) {
                        bytes.sliceArray(44 until bytes.size)
                    } else {
                        bytes
                    }
                    ByteBuffer.wrap(actualAudioData)

                }

                Result.success(Pair(audioData, fileName))


            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        var fileName = ""

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex) ?: fileName
            }
        }

        return fileName
    }
}
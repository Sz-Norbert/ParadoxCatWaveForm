package com.paradoxcat.waveformtest.repositories

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioRepositoryImplTest {

    private val mockContext = mockk<Context>()
    private val mockContentResolver = mockk<ContentResolver>()
    private val mockUri = mockk<Uri>()
    private val mockCursor = mockk<Cursor>()

    private lateinit var audioRepository: AudioRepositoryImpl

    @Before
    fun setup() {
        every { mockContext.contentResolver } returns mockContentResolver
        audioRepository = AudioRepositoryImpl(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun mockCursorWithFilename(fileName: String?) {
        every { mockContentResolver.query(mockUri, null, null, null, null) } returns mockCursor
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { mockCursor.getString(0) } returns fileName
        every { mockCursor.close() } just Runs
    }

    @Test
    fun `loadAudioFile given valid stream and filename returns success`() = runTest {
        val audioBytes = ByteArray(1024)
        val fileName = "test_audio.wav"
        every { mockContentResolver.openInputStream(mockUri) } returns ByteArrayInputStream(audioBytes)
        mockCursorWithFilename(fileName)

        val result = audioRepository.loadAudioFile(mockUri)

        assertTrue(result.isSuccess)
        val (audioData, resultFileName) = result.getOrThrow()
        assertEquals(fileName, resultFileName)
        assertEquals(audioBytes.size, audioData.remaining())
        coVerifyOrder {
            mockContentResolver.openInputStream(mockUri)
            mockContentResolver.query(mockUri, null, null, null, null)
        }
    }

    @Test
    fun `loadAudioFile given wav file skips 44 byte header`() = runTest {
        val header = ByteArray(44).apply { "RIFF".toByteArray().copyInto(this) }
        val audioContent = ByteArray(1024)
        val fileBytes = header + audioContent
        val fileName = "test_audio.wav"
        every { mockContentResolver.openInputStream(mockUri) } returns ByteArrayInputStream(fileBytes)
        mockCursorWithFilename(fileName)

        val result = audioRepository.loadAudioFile(mockUri)

        assertTrue(result.isSuccess)
        val (audioData, _) = result.getOrThrow()
        assertEquals(audioContent.size, audioData.remaining())
    }

    @Test
    fun `loadAudioFile given null input stream returns failure`() = runTest {
        every { mockContentResolver.openInputStream(mockUri) } returns null

        val result = audioRepository.loadAudioFile(mockUri)

        assertTrue(result.isFailure)
        assertEquals("cannot open the file", result.exceptionOrNull()?.message)
        verify(exactly = 1) { mockContentResolver.openInputStream(mockUri) }
        verify(exactly = 0) { mockContentResolver.query(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `loadAudioFile given openInputStream throws IOException returns failure`() = runTest {
        val errorMessage = "IO error"
        every { mockContentResolver.openInputStream(mockUri) } throws IOException(errorMessage)

        val result = audioRepository.loadAudioFile(mockUri)

        assertTrue(result.isFailure)
        assertEquals(errorMessage, result.exceptionOrNull()?.message)
    }

    @Test
    fun `loadAudioFile given null cursor returns success with empty filename`() = runTest {
        val audioBytes = ByteArray(1024)
        every { mockContentResolver.openInputStream(mockUri) } returns ByteArrayInputStream(audioBytes)
        every { mockContentResolver.query(mockUri, null, null, null, null) } returns null

        val result = audioRepository.loadAudioFile(mockUri)

        assertTrue(result.isSuccess)
        val (_, fileName) = result.getOrThrow()
        assertEquals("", fileName)
    }

    @Test
    fun `loadAudioFile given cursor that cannot move to first returns empty filename`() = runTest {
        val audioBytes = ByteArray(1024)
        every { mockContentResolver.openInputStream(mockUri) } returns ByteArrayInputStream(audioBytes)
        every { mockContentResolver.query(mockUri, null, null, null, null) } returns mockCursor
        every { mockCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { mockCursor.moveToFirst() } returns false
        every { mockCursor.close() } just Runs

        val result = audioRepository.loadAudioFile(mockUri)

        assertTrue(result.isSuccess)
        assertEquals("", result.getOrThrow().second)
        verify { mockCursor.moveToFirst() }
        verify(exactly = 0) { mockCursor.getString(any()) }
    }
}

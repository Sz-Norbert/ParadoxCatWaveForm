package com.paradoxcat.waveformtest.services

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlayerServiceImplTest {

    private val mockContext = mockk<Context>()
    private val mockUri = mockk<Uri>()
    private lateinit var audioPlayerService: AudioPlayerService

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkStatic(Looper::class)
        val mockLooper = mockk<Looper>(relaxed = true)
        every { Looper.getMainLooper() } returns mockLooper

        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().setOnCompletionListener(any()) } just Runs
        every { anyConstructed<MediaPlayer>().duration } returns 0
        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } returns true
        every { anyConstructed<Handler>().postDelayed(any(), any()) } returns true
        every { anyConstructed<Handler>().removeCallbacks(any()) } just Runs

        audioPlayerService = AudioPlayerServiceImpl(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `prepareAudio configures MediaPlayer and returns duration`() = runTest {
        val testDuration = 120000
        every { anyConstructed<MediaPlayer>().setDataSource(mockContext, mockUri) } just Runs
        every { anyConstructed<MediaPlayer>().prepare() } just Runs
        every { anyConstructed<MediaPlayer>().duration } returns testDuration
        every { anyConstructed<MediaPlayer>().release() } just Runs

        val result = audioPlayerService.prepareAudio(mockUri)

        assertEquals(testDuration.toLong(), result)
        verify {
            anyConstructed<MediaPlayer>().setDataSource(mockContext, mockUri)
            anyConstructed<MediaPlayer>().prepare()
        }
    }

    @Test
    fun `play starts MediaPlayer and triggers position updates`() = runTest {
        val listener = spyk<(Long) -> Unit>()
        audioPlayerService.setOnPositionUpdateListener(listener)

        every { anyConstructed<MediaPlayer>().setDataSource(any(), any()) } just Runs
        every { anyConstructed<MediaPlayer>().prepare() } just Runs
        every { anyConstructed<MediaPlayer>().release() } just Runs
        audioPlayerService.prepareAudio(mockUri)

        every { anyConstructed<MediaPlayer>().isPlaying } returns true
        every { anyConstructed<MediaPlayer>().currentPosition } returns 1234
        every { anyConstructed<MediaPlayer>().start() } just Runs

        val runnableSlot = slot<Runnable>()
        every { anyConstructed<Handler>().post(capture(runnableSlot)) } answers {
            runnableSlot.captured.run()
            true
        }

        audioPlayerService.play()

        verify { anyConstructed<MediaPlayer>().start() }
        verify { listener.invoke(1234L) }
    }

    @Test
    fun `pause pauses MediaPlayer`() = runTest {
        every { anyConstructed<MediaPlayer>().setDataSource(any(), any()) } just Runs
        every { anyConstructed<MediaPlayer>().prepare() } just Runs
        every { anyConstructed<MediaPlayer>().release() } just Runs
        audioPlayerService.prepareAudio(mockUri)
        every { anyConstructed<MediaPlayer>().pause() } just Runs

        audioPlayerService.pause()

        verify { anyConstructed<MediaPlayer>().pause() }
    }

    @Test
    fun `seekTo seeks MediaPlayer`() = runTest {
        every { anyConstructed<MediaPlayer>().setDataSource(any(), any()) } just Runs
        every { anyConstructed<MediaPlayer>().prepare() } just Runs
        every { anyConstructed<MediaPlayer>().release() } just Runs
        audioPlayerService.prepareAudio(mockUri)
        every { anyConstructed<MediaPlayer>().seekTo(any<Int>()) } just Runs

        val testSeekPosition = 5000L
        audioPlayerService.seekTo(testSeekPosition)

        verify { anyConstructed<MediaPlayer>().seekTo(testSeekPosition.toInt()) }
    }

    @Test
    fun `getCurrentPosition returns position from MediaPlayer`() = runTest {
        every { anyConstructed<MediaPlayer>().setDataSource(any(), any()) } just Runs
        every { anyConstructed<MediaPlayer>().prepare() } just Runs
        every { anyConstructed<MediaPlayer>().release() } just Runs
        audioPlayerService.prepareAudio(mockUri)
        val testCurrentPosition = 15000
        every { anyConstructed<MediaPlayer>().currentPosition } returns testCurrentPosition

        val result = audioPlayerService.getCurrentPosition()

        assertEquals(testCurrentPosition.toLong(), result)
    }

    @Test
    fun `isPlaying returns playing state from MediaPlayer`() = runTest {
        every { anyConstructed<MediaPlayer>().setDataSource(any(), any()) } just Runs
        every { anyConstructed<MediaPlayer>().prepare() } just Runs
        every { anyConstructed<MediaPlayer>().release() } just Runs
        audioPlayerService.prepareAudio(mockUri)
        every { anyConstructed<MediaPlayer>().isPlaying } returns true

        assertTrue(audioPlayerService.isPlaying())
    }

    @Test
    fun `release releases MediaPlayer`() = runTest {
        every { anyConstructed<MediaPlayer>().setDataSource(any(), any()) } just Runs
        every { anyConstructed<MediaPlayer>().prepare() } just Runs
        every { anyConstructed<MediaPlayer>().release() } just Runs
        audioPlayerService.prepareAudio(mockUri)

        audioPlayerService.release()

        verify { anyConstructed<MediaPlayer>().release() }
    }

    @Test
    fun `completion listener is set and triggered`() = runTest {
        val onCompletionSlot = slot<MediaPlayer.OnCompletionListener>()
        val onCompletionCallback = spyk<() -> Unit>()
        audioPlayerService.setOnCompletionListener(onCompletionCallback)

        every { anyConstructed<MediaPlayer>().setOnCompletionListener(capture(onCompletionSlot)) } just Runs

        every { anyConstructed<MediaPlayer>().setDataSource(any(), any()) } just Runs
        every { anyConstructed<MediaPlayer>().prepare() } just Runs
        every { anyConstructed<MediaPlayer>().release() } just Runs
        audioPlayerService.prepareAudio(mockUri)

        assertTrue(onCompletionSlot.isCaptured)
        onCompletionSlot.captured.onCompletion(mockk())
        verify { onCompletionCallback.invoke() }
    }

    @Test
    fun `operations on null MediaPlayer do not crash`() {
        audioPlayerService.play()
        audioPlayerService.pause()
        audioPlayerService.seekTo(1000L)
        assertEquals(0L, audioPlayerService.getCurrentPosition())
        assertFalse(audioPlayerService.isPlaying())
    }
}

package com.paradoxcat.waveformtest.viewmodels

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.paradoxcat.waveformtest.repositories.AudioRepository
import com.paradoxcat.waveformtest.services.AudioPlayerService
import com.paradoxcat.waveformtest.viewmodels.uiStates.WaveformUiState
import com.paradoxcat.waveformtest.viewmodels.uiStates.WaveformViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer

@ExperimentalCoroutinesApi
class WaveformViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private val mockAudioRepository = mockk<AudioRepository>()
    private val mockAudioPlayerService = mockk<AudioPlayerService>(relaxed = true)
    private val mockUiStateObserver = mockk<Observer<WaveformUiState>>(relaxed = true)

    private lateinit var viewModel: WaveformViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = WaveformViewModel(mockAudioRepository, mockAudioPlayerService)
        viewModel.uiState.observeForever(mockUiStateObserver)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state is correctly set`() {
        val initialState = viewModel.uiState.value
        assertNotNull(initialState)
        with(initialState!!) {
            assertFalse(isLoading)
            assertFalse(hasData)
            assertFalse(hasError)
            assertFalse(isPlaying)
            assertEquals(0L, currentPosition)
            assertEquals(0L, totalDuration)
            assertNull(audioData)
            assertNull(errorMessage)
        }
    }

    @Test
    fun `loadAudioFile on success updates state with data and duration`() = runTest {
        val mockUri = mockk<Uri>()
        val mockAudioData = mockk<ByteBuffer>()
        val fileName = "test.wav"
        val duration = 120000L
        coEvery { mockAudioRepository.loadAudioFile(mockUri) } returns Result.success(Pair(mockAudioData, fileName))
        coEvery { mockAudioPlayerService.prepareAudio(mockUri) } returns duration

        clearMocks(mockUiStateObserver, answers = false)
        viewModel.loadAudioFile(mockUri)
        testDispatcher.scheduler.advanceUntilIdle()

        val states = mutableListOf<WaveformUiState>()
        verify { mockUiStateObserver.onChanged(capture(states)) }

        assertTrue(states.first().isLoading)
        val finalState = states.last()
        assertFalse(finalState.isLoading)
        assertTrue(finalState.hasData)
        assertEquals(fileName, finalState.fileName)
        assertEquals(duration, finalState.totalDuration)
        assertEquals(mockAudioData, finalState.audioData)

        coVerifyOrder {
            mockAudioRepository.loadAudioFile(mockUri)
            mockAudioPlayerService.prepareAudio(mockUri)
        }
    }

    @Test
    fun `loadAudioFile on repository failure updates state with error`() = runTest {
        val mockUri = mockk<Uri>()
        val rawErrorMessage = "Failed to load file"
        val expectedErrorMessage = "Cannot upload file : $rawErrorMessage"
        coEvery { mockAudioRepository.loadAudioFile(mockUri) } returns Result.failure(Exception(rawErrorMessage))

        clearMocks(mockUiStateObserver, answers = false)
        viewModel.loadAudioFile(mockUri)
        testDispatcher.scheduler.advanceUntilIdle()

        val states = mutableListOf<WaveformUiState>()
        verify { mockUiStateObserver.onChanged(capture(states)) }

        assertTrue(states.first().isLoading)
        val finalState = states.last()
        assertFalse(finalState.isLoading)
        assertTrue(finalState.hasError)
        assertEquals(expectedErrorMessage, finalState.errorMessage)

        coVerify(exactly = 0) { mockAudioPlayerService.prepareAudio(any()) }
    }

    @Test
    fun `togglePlayPause when paused and has data starts playing`() = runTest {
        loadAudioSuccessfully()

        viewModel.togglePlayPause()

        verify { mockAudioPlayerService.play() }
        val finalState = viewModel.uiState.value!!
        assertTrue(finalState.isPlaying)
    }

    @Test
    fun `togglePlayPause when playing pauses playback`() = runTest {
        loadAudioSuccessfully()
        viewModel.togglePlayPause() 

        viewModel.togglePlayPause()

        verify { mockAudioPlayerService.pause() }
        val finalState = viewModel.uiState.value!!
        assertFalse(finalState.isPlaying)
    }

    @Test
    fun `seekTo updates position and calls service`() = runTest {
        loadAudioSuccessfully()
        val seekPosition = 30000L

        viewModel.seekTo(seekPosition)

        verify { mockAudioPlayerService.seekTo(seekPosition) }
        assertEquals(seekPosition, viewModel.uiState.value!!.currentPosition)
    }

    @Test
    fun `seekToPercentage calculates position and calls service`() = runTest {
        val duration = 200000L
        loadAudioSuccessfully(duration = duration)
        val percentage = 0.5f
        val expectedPosition = (duration * percentage).toLong()

        viewModel.seekToPercentage(percentage)

        verify { mockAudioPlayerService.seekTo(expectedPosition) }
        assertEquals(expectedPosition, viewModel.uiState.value!!.currentPosition)
    }

    @Test
    fun `position update from service updates ui state`() {
        val positionCallback = slot<(Long) -> Unit>()
        every { mockAudioPlayerService.setOnPositionUpdateListener(capture(positionCallback)) } just Runs
        val localViewModel = WaveformViewModel(mockAudioRepository, mockAudioPlayerService)
        val newPosition = 45000L

        positionCallback.captured.invoke(newPosition)

        assertEquals(newPosition, localViewModel.uiState.value!!.currentPosition)
    }

    @Test
    fun `completion from service resets playing state and position`() {
        val completionCallback = slot<() -> Unit>()
        every { mockAudioPlayerService.setOnCompletionListener(capture(completionCallback)) } just Runs
        val localViewModel = WaveformViewModel(mockAudioRepository, mockAudioPlayerService)
        runTest {
            loadAudioSuccessfully(viewModel = localViewModel)
            localViewModel.togglePlayPause()
        }
        assertTrue(localViewModel.uiState.value!!.isPlaying)

        completionCallback.captured.invoke()

        val finalState = localViewModel.uiState.value!!
        assertFalse(finalState.isPlaying)
        assertEquals(0L, finalState.currentPosition)
    }

    private suspend fun loadAudioSuccessfully(duration: Long = 120000L, viewModel: WaveformViewModel = this.viewModel) {
        val mockUri = mockk<Uri>()
        val mockAudioData = mockk<ByteBuffer>()
        val fileName = "test.wav"
        coEvery { mockAudioRepository.loadAudioFile(mockUri) } returns Result.success(Pair(mockAudioData, fileName))
        coEvery { mockAudioPlayerService.prepareAudio(mockUri) } returns duration
        viewModel.loadAudioFile(mockUri)
        testDispatcher.scheduler.advanceUntilIdle()
    }
}

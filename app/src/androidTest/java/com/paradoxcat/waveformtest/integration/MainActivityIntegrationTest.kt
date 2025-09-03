package com.paradoxcat.waveformtest.integration

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.paradoxcat.waveformtest.MainActivity
import com.paradoxcat.waveformtest.R
import com.paradoxcat.waveformtest.di.RepositoryModule
import com.paradoxcat.waveformtest.repositories.AudioRepository
import com.paradoxcat.waveformtest.services.AudioPlayerService
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.*
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
@UninstallModules(RepositoryModule::class)
class MainActivityIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @BindValue
    @JvmField
    val mockAudioRepository: AudioRepository = mockk()

    @BindValue
    @JvmField
    val mockAudioPlayerService: AudioPlayerService = mockk(relaxed = true)

    private var positionUpdateCallback: ((Long) -> Unit)? = null
    private var completionCallback: (() -> Unit)? = null

    @Before
    fun setup() {
        Intents.init()
        hiltRule.inject()

        val mockAudioData = mockk<ByteBuffer>()
        every { mockAudioData.remaining() } returns 2048

        coEvery { mockAudioRepository.loadAudioFile(any()) } returns Result.success(Pair(mockAudioData, "test.wav"))
        coEvery { mockAudioPlayerService.prepareAudio(any()) } returns 150000L

        every { mockAudioPlayerService.setOnPositionUpdateListener(any()) } answers { positionUpdateCallback = firstArg() }
        every { mockAudioPlayerService.setOnCompletionListener(any()) } answers { completionCallback = firstArg() }
    }

    @After
    fun tearDown() {
        Intents.release()
        unmockkAll()
    }

    @Test
    fun playButton_triggersServiceCalls() {
        onView(withId(R.id.playButton)).perform(click())
        verify { mockAudioPlayerService.play() }

        onView(withId(R.id.playButton)).perform(click())
        verify { mockAudioPlayerService.pause() }
    }

    @Test
    fun waveformView_onClick_seeksAudio() {
        onView(withId(R.id.waveformView)).perform(click())
        verify { mockAudioPlayerService.seekTo(any()) }
    }

    @Test
    fun positionUpdateCallback_updatesCurrentTimeText() {
        positionUpdateCallback?.invoke(75000L)
        onView(withId(R.id.currentTimeText)).check(matches(withText("1:15")))
    }

    @Test
    fun completionCallback_resetsPlaybackState() {
        onView(withId(R.id.playButton)).perform(click())
        onView(withId(R.id.playButton)).check(matches(withText(R.string.pause)))

        completionCallback?.invoke()

        onView(withId(R.id.playButton)).check(matches(withText(R.string.play)))
        onView(withId(R.id.currentTimeText)).check(matches(withText("0:00")))
    }

    @Test
    fun loadButton_launchesFilePickerIntent() {
        activityScenarioRule.scenario.recreate() // Recreate to ensure we are in initial state
        onView(withId(R.id.loadButton)).perform(click())
        Intents.intended(allOf(hasAction(Intent.ACTION_GET_CONTENT), hasType("audio/*")))
    }

    @Test
    fun filePicker_onResult_loadsAudio() {
        val resultData = Intent().setData(Uri.parse("content://test/audio.wav"))
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, resultData)

        Intents.intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(result)

        activityScenarioRule.scenario.recreate()
        onView(withId(R.id.loadButton)).perform(click())

        onView(withId(R.id.playButton)).check(matches(isEnabled()))
        coVerify { mockAudioRepository.loadAudioFile(resultData.data!!) }
    }

    @Test
    fun activityLifecycle_onStop_releasesPlayer() {
        activityScenarioRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.DESTROYED)
        verify { mockAudioPlayerService.release() }
    }
}

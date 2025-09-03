package com.paradoxcat.waveformtest.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
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
class MainActivityUiTest {

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

    @Before
    fun setup() {
        hiltRule.inject()

        val mockAudioData = mockk<ByteBuffer>()
        every { mockAudioData.remaining() } returns 2048
        coEvery { mockAudioRepository.loadAudioFile(any()) } returns Result.success(Pair(mockAudioData, "test.wav"))
        coEvery { mockAudioPlayerService.prepareAudio(any()) } returns 150000L
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun initialState_showsDefaultUI() {
        activityScenarioRule.scenario.recreate()

        onView(withId(R.id.statusText)).check(matches(withText(R.string.upload_audio_prompt)))
        onView(withId(R.id.loadButton)).check(matches(isEnabled()))
        onView(withId(R.id.playButton)).check(matches(not(isEnabled())))
        onView(withId(R.id.timelineSeekBar)).check(matches(not(isEnabled())))
        onView(withId(R.id.currentTimeText)).check(matches(withText("0:00")))
        onView(withId(R.id.totalTimeText)).check(matches(withText("0:00")))
        onView(withId(R.id.loadingAnimation)).check(matches(not(isDisplayed())))
    }

    @Test
    fun loadAudio_onSuccess_enablesPlaybackControls() {
        onView(withId(R.id.playButton)).check(matches(isEnabled()))
        onView(withId(R.id.timelineSeekBar)).check(matches(isEnabled()))
        onView(withId(R.id.totalTimeText)).check(matches(withText("2:30")))
    }

    @Test
    fun playButton_togglesPlayAndPause_textOnly() {
        onView(withId(R.id.playButton)).perform(click())
        onView(withId(R.id.playButton)).check(matches(withText(R.string.pause)))

        onView(withId(R.id.playButton)).perform(click())
        onView(withId(R.id.playButton)).check(matches(withText(R.string.play)))
    }
}

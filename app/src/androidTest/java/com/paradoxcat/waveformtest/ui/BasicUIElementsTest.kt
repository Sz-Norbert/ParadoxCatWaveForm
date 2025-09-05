package com.paradoxcat.waveformtest.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.CoreMatchers.not
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paradoxcat.waveformtest.MainActivity
import com.paradoxcat.waveformtest.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasicUIElementsTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun allButtonsAndViewsAreDisplayed() {
        onView(withId(R.id.playButton)).check(matches(isDisplayed()))
        onView(withId(R.id.loadButton)).check(matches(isDisplayed()))
        onView(withId(R.id.waveformView)).check(matches(isDisplayed()))
        onView(withId(R.id.currentTimeText)).check(matches(isDisplayed()))
        onView(withId(R.id.totalTimeText)).check(matches(isDisplayed()))
    }

    @Test
    fun playButtonShowsPlayText() {
        onView(withId(R.id.playButton)).check(matches(withText(R.string.play)))
    }

    @Test
    fun loadButtonIsClickable() {
        onView(withId(R.id.loadButton)).check(matches(isClickable()))
        onView(withId(R.id.loadButton)).perform(click())
    }

    @Test
    fun timeDisplaysShowZeroAtStart() {
        onView(withId(R.id.currentTimeText)).check(matches(withText("0:00")))
        onView(withId(R.id.totalTimeText)).check(matches(withText("0:00")))
    }

    @Test
    fun playButtonStartsDisabled() {
        onView(withId(R.id.playButton)).check(matches(not(isEnabled())))
    }

    @Test
    fun waveformViewIsVisible() {
        onView(withId(R.id.waveformView)).check(matches(isDisplayed()))
    }
}
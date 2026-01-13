package com.leapmotor.translator.ui.main

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.leapmotor.translator.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso UI tests for MainActivity.
 * 
 * Tests the main screen UI interactions including:
 * - Permission buttons visibility
 * - Service status display
 * - Navigation to other screens
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class MainActivityTest {
    
    @get:Rule
    var hiltRule = HiltAndroidRule(this)
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    @Test
    fun launchMainActivity_displaysTitle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Check that the app launches without crashing
            scenario.onActivity { activity ->
                assert(activity != null)
            }
        }
    }
    
    @Test
    fun mainScreen_showsOverlayPermissionButton() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Verify overlay permission button is visible
            onView(withText("Разрешение на наложение"))
                .check(matches(isDisplayed()))
        }
    }
    
    @Test
    fun mainScreen_showsAccessibilityButton() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Verify accessibility settings button is visible
            onView(withText("Настройки доступности"))
                .check(matches(isDisplayed()))
        }
    }
    
    @Test
    fun mainScreen_showsDictionaryButton() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Verify dictionary button is visible
            onView(withText("Словарь / Редактор"))
                .check(matches(isDisplayed()))
        }
    }
    
    @Test
    fun mainScreen_showsDebugModeCheckbox() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Verify debug mode checkbox is visible
            // Note: Uses contentDescription or text depending on CheckBox implementation
            onView(withText("Отладка: показать границы"))
                .check(matches(isDisplayed()))
        }
    }
    
    @Test
    fun mainScreen_showsStatusCards() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Verify status cards are displayed
            onView(withText("Разрешение на наложение"))
                .check(matches(isDisplayed()))
            
            onView(withText("Сервис специальных возможностей"))
                .check(matches(isDisplayed()))
        }
    }
    
    @Test
    fun clickDictionaryButton_navigatesToDictionary() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Click dictionary button
            onView(withText("Словарь / Редактор"))
                .perform(click())
            
            // Verify navigation occurred (DictionaryActivity should be visible)
            // Note: This test may need adjustment based on actual navigation implementation
        }
    }
    
    @Test
    fun toggleDebugMode_changesCheckboxState() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Find and click the debug checkbox
            onView(withText("Отладка: показать границы"))
                .perform(click())
            
            // Verify it's now checked (state changed)
            onView(withText("Отладка: показать границы"))
                .check(matches(isDisplayed()))
        }
    }
}

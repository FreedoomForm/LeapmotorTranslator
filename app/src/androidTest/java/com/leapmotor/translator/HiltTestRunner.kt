package com.leapmotor.translator

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom test runner for Hilt instrumented tests.
 * 
 * Configured in build.gradle.kts:
 * testInstrumentationRunner = "com.leapmotor.translator.HiltTestRunner"
 */
class HiltTestRunner : AndroidJUnitRunner() {
    
    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(classLoader, HiltTestApplication::class.java.name, context)
    }
}

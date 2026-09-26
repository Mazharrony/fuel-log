package com.fuelexpenselog.app.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** viewModelScope runs on Dispatchers.Main; tests swap it for a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    /**
     * A ViewModel can still have Room work in flight on a background thread when the test
     * ends, and its continuation lands on Main just as it is being reset - which throws
     * "Dispatchers.Main is used concurrently with setting it". That dispatch takes
     * microseconds, so the reset simply waits it out.
     */
    override fun finished(description: Description) {
        repeat(RESET_ATTEMPTS) {
            try {
                Dispatchers.resetMain()
                return
            } catch (inFlight: IllegalStateException) {
                Thread.sleep(10)
            }
        }
        Dispatchers.resetMain()
    }

    private companion object {
        const val RESET_ATTEMPTS = 50
    }
}

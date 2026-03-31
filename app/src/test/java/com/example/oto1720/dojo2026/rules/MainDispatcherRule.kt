package com.example.oto1720.dojo2026.rules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * A JUnit Test Rule that swaps the Main dispatcher for a TestDispatcher.
 * This is essential for testing ViewModels that use viewModelScope.
 *
 * Using StandardTestDispatcher gives us full control over the execution of coroutines.
 * We need to explicitly call `runCurrent()` or `advanceUntilIdle()` to execute pending coroutines.
 *
 * Example usage:
 * ```
 * @get:Rule
 * val mainDispatcherRule = MainDispatcherRule()
 *
 * @Test
 * fun someTest() = runTest { // runTest from kotlinx-coroutines-test
 *     // ... test code ...
 *     mainDispatcherRule.testDispatcher.scheduler.runCurrent() // or advanceUntilIdle()
 * }
 * ```
 */
@ExperimentalCoroutinesApi
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

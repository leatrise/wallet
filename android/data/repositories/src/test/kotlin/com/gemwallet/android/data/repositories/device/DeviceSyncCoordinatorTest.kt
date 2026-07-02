package com.gemwallet.android.data.repositories.device

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DeviceSyncCoordinatorTest {

    @Test
    fun coalescesConcurrentCallsIntoSingleRun() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = DeviceSyncCoordinator(scope)
        val runs = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()

        repeat(3) {
            scope.launch {
                coordinator.coordinate {
                    runs.incrementAndGet()
                    gate.await()
                }
            }
        }
        assertEquals(1, runs.get())

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, runs.get())
        scope.cancel()
    }

    @Test
    fun runsAgainForCallAfterPreviousCompleted() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = DeviceSyncCoordinator(scope)
        val runs = AtomicInteger(0)

        coordinator.coordinate { runs.incrementAndGet() }
        coordinator.coordinate { runs.incrementAndGet() }

        assertEquals(2, runs.get())
        scope.cancel()
    }

    @Test
    fun waitForSyncIfNeededAwaitsInFlightSync() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = DeviceSyncCoordinator(scope)
        val gate = CompletableDeferred<Unit>()
        val opCompleted = AtomicBoolean(false)
        scope.launch {
            coordinator.coordinate {
                gate.await()
                opCompleted.set(true)
            }
        }

        val waitCompleted = AtomicBoolean(false)
        scope.launch {
            coordinator.waitForSyncIfNeeded()
            waitCompleted.set(true)
        }
        assertFalse(waitCompleted.get())

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(opCompleted.get())
        assertTrue(waitCompleted.get())
        scope.cancel()
    }
}

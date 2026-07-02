package com.gemwallet.android.data.repositories.device

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DeviceSyncCoordinator(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private var syncJob: Deferred<Unit>? = null

    suspend fun waitForSyncIfNeeded() {
        val job = mutex.withLock { syncJob }
        job?.let { runCatching { it.await() } }
    }

    suspend fun coordinate(operation: suspend () -> Unit) {
        val job = mutex.withLock {
            syncJob?.takeIf { it.isActive } ?: scope.async { operation() }.also { syncJob = it }
        }
        try {
            runCatching { job.await() }
        } finally {
            mutex.withLock { if (syncJob === job) syncJob = null }
        }
    }
}

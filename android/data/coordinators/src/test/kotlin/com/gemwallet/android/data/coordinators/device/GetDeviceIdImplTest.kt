package com.gemwallet.android.data.coordinators.device

import com.gemwallet.android.application.SecurityStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetDeviceIdImplTest {

    @Test
    fun `constructor does not read secure storage`() {
        val store = RecordingSecurityStore(
            values = mapOf(
                GetDeviceIdImpl.Keys.PrivateKey.toString() to "private",
                GetDeviceIdImpl.Keys.PublicKey.toString() to "public",
            )
        )

        GetDeviceIdImpl(store)

        assertEquals(0, store.readCount)
    }

    @Test
    fun `device keys are loaded lazily and cached`() = runTest {
        val store = RecordingSecurityStore(
            values = mapOf(
                GetDeviceIdImpl.Keys.PrivateKey.toString() to "private",
                GetDeviceIdImpl.Keys.PublicKey.toString() to "public",
            )
        )
        val deviceId = GetDeviceIdImpl(store)

        assertEquals("public", deviceId.getDeviceId())
        assertEquals("private", deviceId.getDeviceKey())

        assertEquals(2, store.readCount)
        assertEquals(0, store.writeCount)
    }

    @Test
    fun `existing keys are not rotated when read fails`() = runTest {
        val store = RecordingSecurityStore(
            values = mapOf(
                GetDeviceIdImpl.Keys.PrivateKey.toString() to "private",
                GetDeviceIdImpl.Keys.PublicKey.toString() to "public",
            ),
            failReadWith = IllegalStateException("Keystore operation failed"),
        )
        val deviceId = GetDeviceIdImpl(store)

        val error = runCatching { deviceId.getDeviceId() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(0, store.writeCount)
    }

    private class RecordingSecurityStore(
        private val values: Map<String, String>,
        private val failReadWith: Throwable? = null,
    ) : SecurityStore<Any> {

        var readCount = 0
            private set
        var writeCount = 0
            private set

        override suspend fun contains(key: Any): Boolean = values.containsKey(key.toString())

        override suspend fun getValue(key: Any): String {
            readCount += 1
            failReadWith?.let { throw it }
            return values.getValue(key.toString())
        }

        override suspend fun putValue(key: Any, value: String) {
            writeCount += 1
        }
    }
}

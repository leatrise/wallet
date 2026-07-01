package com.gemwallet.android.services

import com.gemwallet.android.application.fiat.coordinators.SyncFiatAssets
import com.gemwallet.android.cases.device.SyncDeviceInfo
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

class SyncServiceTest {
    private val syncFiatAssets = mockk<SyncFiatAssets>(relaxed = true)
    private val syncDeviceInfo = mockk<SyncDeviceInfo>(relaxed = true)

    private val subject = SyncService(
        syncFiatAssets = syncFiatAssets,
        syncDeviceInfo = syncDeviceInfo,
    )

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun sync_registersDeviceBeforeSyncingFiatAssets() = runBlocking {
        subject.sync()

        coVerifyOrder {
            syncDeviceInfo.syncDeviceInfo()
            syncFiatAssets()
        }
    }
}

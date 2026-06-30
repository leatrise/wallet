package com.gemwallet.android.ext

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformTest {

    @Test
    fun androidOsDisplayName_includesSdkVersion() {
        assertEquals("Android 15 (SDK 35)", androidOsDisplayName(version = "15", sdk = 35))
    }
}

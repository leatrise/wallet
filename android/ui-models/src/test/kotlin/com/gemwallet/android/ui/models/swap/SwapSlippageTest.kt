package com.gemwallet.android.ui.models.swap

import org.junit.Assert.assertEquals
import org.junit.Test

class SwapSlippageTest {
    @Test
    fun format_trimsTrailingZeros() {
        assertEquals("1", SwapSlippage.format(100u))
        assertEquals("0.5", SwapSlippage.format(50u))
        assertEquals("0.1", SwapSlippage.format(10u))
        assertEquals("5", SwapSlippage.format(500u))
    }

    @Test
    fun label_appendsPercent() {
        assertEquals("1%", SwapSlippage.label(100u))
        assertEquals("5%", SwapSlippage.label(500u))
    }
}

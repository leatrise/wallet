package com.gemwallet.android.ui.models.swap

object SwapSlippage {
    val minBps: UInt = 10u
    val maxBps: UInt = 500u
    val stepBps: UInt = 10u
    val defaultBps: UInt = 100u

    fun format(bps: UInt): String =
        bps.toLong().toBigDecimal().movePointLeft(2).stripTrailingZeros().toPlainString()

    fun label(bps: UInt): String = "${format(bps)}%"
}

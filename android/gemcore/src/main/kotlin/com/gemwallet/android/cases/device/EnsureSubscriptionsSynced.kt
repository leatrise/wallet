package com.gemwallet.android.cases.device

fun interface EnsureSubscriptionsSynced {
    suspend operator fun invoke()
}

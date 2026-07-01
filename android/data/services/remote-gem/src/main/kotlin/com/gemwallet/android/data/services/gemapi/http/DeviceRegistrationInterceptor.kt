package com.gemwallet.android.data.services.gemapi.http

import com.gemwallet.android.cases.device.EnsureDeviceRegistered
import com.wallet.core.primitives.WalletId
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class DeviceRegistrationInterceptor(
    private val ensureDeviceRegistered: EnsureDeviceRegistered,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.tag(WalletId::class.java) != null) {
            runCatching { runBlocking { ensureDeviceRegistered() } }
        }
        return chain.proceed(request)
    }
}

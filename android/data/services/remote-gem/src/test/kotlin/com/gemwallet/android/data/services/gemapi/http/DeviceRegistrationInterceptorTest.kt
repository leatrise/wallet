package com.gemwallet.android.data.services.gemapi.http

import com.gemwallet.android.cases.device.EnsureDeviceRegistered
import com.gemwallet.android.testkit.mockMulticoinWalletId
import com.wallet.core.primitives.WalletId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DeviceRegistrationInterceptorTest {
    private val events = mutableListOf<String>()
    private val ensureDeviceRegistered = mockk<EnsureDeviceRegistered>()
    private val subject = DeviceRegistrationInterceptor(ensureDeviceRegistered)

    @Before
    fun setUp() {
        coEvery { ensureDeviceRegistered() } answers { events.add("preflight") }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun walletScopedRequest_runsRegistrationPreflightBeforeProceeding() {
        val request = Request.Builder()
            .url("https://api.gemwallet.com/v2/devices/rewards")
            .tag(WalletId::class.java, mockMulticoinWalletId())
            .build()

        subject.intercept(RecordingChain(request, events))

        assertEquals(listOf("preflight", "proceed"), events)
    }

    @Test
    fun walletScopedRequest_proceedsEvenWhenPreflightFails() {
        coEvery { ensureDeviceRegistered() } throws RuntimeException("registration failed")
        val request = Request.Builder()
            .url("https://api.gemwallet.com/v2/devices/rewards")
            .tag(WalletId::class.java, mockMulticoinWalletId())
            .build()

        val response = subject.intercept(RecordingChain(request, events))

        assertEquals(200, response.code)
        assertEquals(listOf("proceed"), events)
    }

    @Test
    fun deviceScopedRequest_proceedsWithoutRegistrationPreflight() {
        val request = Request.Builder()
            .url("https://api.gemwallet.com/v2/devices")
            .build()

        subject.intercept(RecordingChain(request, events))

        coVerify(exactly = 0) { ensureDeviceRegistered() }
        assertEquals(listOf("proceed"), events)
    }
}

private class RecordingChain(
    private val request: Request,
    private val events: MutableList<String>,
) : Interceptor.Chain {
    override fun request(): Request = request

    override fun proceed(request: Request): Response {
        events.add("proceed")
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .build()
    }

    override fun connection(): Connection? = null

    override fun call(): Call = error("not used")

    override fun connectTimeoutMillis(): Int = 0

    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override fun readTimeoutMillis(): Int = 0

    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override fun writeTimeoutMillis(): Int = 0

    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
}

package com.gemwallet.android.data.services.gemapi.di

import android.content.Context
import android.os.Build
import com.gemwallet.android.Constants
import com.gemwallet.android.application.device.coordinators.GetDeviceId
import com.gemwallet.android.cases.device.EnsureDeviceRegistered
import com.gemwallet.android.cases.device.EnsureSubscriptionsSynced
import com.gemwallet.android.data.services.gemapi.GemApiClient
import com.gemwallet.android.data.services.gemapi.GemApiStaticClient
import com.gemwallet.android.data.services.gemapi.GemDeviceApiClient
import com.gemwallet.android.data.services.gemapi.Mime
import com.gemwallet.android.data.services.gemapi.http.DeviceRegistrationInterceptor
import com.gemwallet.android.data.services.gemapi.http.GemApiErrorInterceptor
import com.gemwallet.android.data.services.gemapi.http.SecurityInterceptor
import com.gemwallet.android.data.services.gemapi.http.SubscriptionSyncInterceptor
import com.gemwallet.android.model.BuildInfo
import com.gemwallet.android.serializer.jsonEncoder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object ClientsModule {

    @Provides
    @Singleton
    fun provideDeviceRegistrationInterceptor(
        ensureDeviceRegistered: EnsureDeviceRegistered,
    ) = DeviceRegistrationInterceptor(ensureDeviceRegistered)

    @Provides
    @Singleton
    fun provideSubscriptionSyncInterceptor(
        ensureSubscriptionsSynced: EnsureSubscriptionsSynced,
    ) = SubscriptionSyncInterceptor(ensureSubscriptionsSynced)

    @Provides
    @Singleton
    fun provideSecurityInterceptor(getDeviceId: GetDeviceId) = SecurityInterceptor(getDeviceId)

    @Provides
    @Singleton
    fun provideGemApiErrorInterceptor() = GemApiErrorInterceptor()

    @Provides
    @Singleton
    fun provideGemHttpClient(
        @ApplicationContext context: Context,
        buildInfo: BuildInfo,
        gemApiErrorInterceptor: GemApiErrorInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
        .cache(Cache(context.cacheDir, 10 * 1024 * 1024))
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request()
                    .newBuilder()
                    .header("User-Agent", "Gem/${buildInfo.versionCode} Android/${Build.VERSION.RELEASE} Version/${buildInfo.versionName}")
                    .build()
            )
        }
        .addInterceptor(gemApiErrorInterceptor)
        .build()

    @Provides
    @Singleton
    fun provideGemApiClient(httpClient: OkHttpClient): GemApiClient =
        Retrofit.Builder()
            .baseUrl(Constants.API_URL)
            .client(httpClient)
            .addConverterFactory(jsonEncoder.asConverterFactory(Mime.Json.value))
            .build()
            .create(GemApiClient::class.java)

    @Provides
    @Singleton
    @DeviceApiWithoutRegistration
    fun provideDeviceApiWithoutRegistration(
        httpClient: OkHttpClient,
        securityInterceptor: SecurityInterceptor,
    ): GemDeviceApiClient = deviceApiClient(httpClient, securityInterceptor)

    @Provides
    @Singleton
    fun provideGemDeviceApiClient(
        httpClient: OkHttpClient,
        deviceRegistrationInterceptor: DeviceRegistrationInterceptor,
        subscriptionSyncInterceptor: SubscriptionSyncInterceptor,
        securityInterceptor: SecurityInterceptor,
    ): GemDeviceApiClient = deviceApiClient(httpClient, deviceRegistrationInterceptor, subscriptionSyncInterceptor, securityInterceptor)

    private fun deviceApiClient(
        httpClient: OkHttpClient,
        vararg interceptors: Interceptor,
    ): GemDeviceApiClient {
        val client = httpClient.newBuilder()
            .dispatcher(Dispatcher())
            .apply { interceptors.forEach { addInterceptor(it) } }
            .build()
        return Retrofit.Builder()
            .baseUrl(Constants.API_URL)
            .client(client)
            .addConverterFactory(jsonEncoder.asConverterFactory(Mime.Json.value))
            .build()
            .create(GemDeviceApiClient::class.java)
    }

    @Provides
    @Singleton
    fun provideGemApiStaticClient(httpClient: OkHttpClient): GemApiStaticClient {
        return Retrofit.Builder()
            .baseUrl(Constants.ASSETS_URL)
            .client(httpClient)
            .addConverterFactory(jsonEncoder.asConverterFactory(Mime.Json.value))
            .build()
            .create(GemApiStaticClient::class.java)
    }
}

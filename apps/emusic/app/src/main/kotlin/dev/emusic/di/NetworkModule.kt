package dev.emusic.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.emusic.data.api.CredentialProvider
import dev.emusic.data.api.SubsonicApiService
import dev.emusic.data.api.SubsonicAuthInterceptor
import kotlinx.serialization.json.Json
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: SubsonicAuthInterceptor,
    ): OkHttpClient {
        // Trust all certificates — safe for private LAN Navidrome over Tailscale
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }

        val cache = Cache(
            directory = java.io.File(context.cacheDir, "http_cache"),
            maxSize = 50L * 1024 * 1024, // 50 MB
        )

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(authInterceptor)
            .cache(cache)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
        credentialProvider: CredentialProvider,
    ): Retrofit {
        // Dynamic base URL interceptor — reads serverUrl at request time, not build time
        val dynamicClient = client.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                val serverUrl = credentialProvider.serverUrl.trimEnd('/')
                if (serverUrl.isNotEmpty()) {
                    val originalUrl = original.url
                    val query = originalUrl.encodedQuery?.let { "?$it" } ?: ""
                    val newUrl = "$serverUrl${originalUrl.encodedPath}$query".toHttpUrl()
                    val newRequest = original.newBuilder().url(newUrl).build()
                    return@addInterceptor chain.proceed(newRequest)
                }
                chain.proceed(original)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl("http://placeholder.invalid/")
            .client(dynamicClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideSubsonicApiService(retrofit: Retrofit): SubsonicApiService =
        retrofit.create(SubsonicApiService::class.java)
}

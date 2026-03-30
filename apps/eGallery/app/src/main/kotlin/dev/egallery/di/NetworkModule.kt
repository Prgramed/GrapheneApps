package dev.egallery.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.egallery.api.ImmichAuthInterceptor
import dev.egallery.api.ImmichPhotoService
import dev.egallery.data.CredentialStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: ImmichAuthInterceptor,
        credentialStore: CredentialStore,
    ): OkHttpClient {
        // Trust all certs for local/self-signed Immich servers
        val trustManager = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<javax.net.ssl.TrustManager>(trustManager), java.security.SecureRandom())

        return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier { _, _ -> true }
        .addInterceptor(authInterceptor)
        // Rewrite base URL to the actual Immich server at request time
        .addInterceptor { chain ->
            val original = chain.request()
            val serverUrl = credentialStore.serverUrl.trimEnd('/')
            if (serverUrl.isNotBlank() && original.url.host == "placeholder.local") {
                val baseUrl = serverUrl.toHttpUrlOrNull()
                if (baseUrl != null) {
                    val newUrl = original.url.newBuilder()
                        .scheme(baseUrl.scheme)
                        .host(baseUrl.host)
                        .port(baseUrl.port)
                        .build()
                    chain.proceed(original.newBuilder().url(newUrl).build())
                } else {
                    chain.proceed(original)
                }
            } else {
                chain.proceed(original)
            }
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl("http://placeholder.local/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideImmichPhotoService(retrofit: Retrofit): ImmichPhotoService =
        retrofit.create(ImmichPhotoService::class.java)
}

package dev.egallery.api

import dev.egallery.data.CredentialStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that adds the Immich API key to every request.
 * Simple header-based auth — no sessions, cookies, CSRF tokens, or re-auth.
 */
@Singleton
class ImmichAuthInterceptor @Inject constructor(
    private val credentialStore: CredentialStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = credentialStore.apiKey
        val request = if (apiKey.isNotBlank()) {
            chain.request().newBuilder()
                .addHeader("x-api-key", apiKey)
                .addHeader("Accept", "application/json")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

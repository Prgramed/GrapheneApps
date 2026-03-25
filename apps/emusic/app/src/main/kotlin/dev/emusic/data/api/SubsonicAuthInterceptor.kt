package dev.emusic.data.api

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubsonicAuthInterceptor @Inject constructor(
    private val credentialProvider: CredentialProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val salt = generateSalt()
        val token = md5("${credentialProvider.password}$salt")

        val url = original.url.newBuilder()
            .addQueryParameter("u", credentialProvider.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")
            .build()

        return chain.proceed(original.newBuilder().url(url).build())
    }

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "emusic"
        private const val SALT_LENGTH = 12
        private val secureRandom = SecureRandom()

        fun generateSalt(length: Int = SALT_LENGTH): String {
            val bytes = ByteArray(length)
            secureRandom.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun md5(input: String): String {
            val digest = MessageDigest.getInstance("MD5")
            return digest.digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}

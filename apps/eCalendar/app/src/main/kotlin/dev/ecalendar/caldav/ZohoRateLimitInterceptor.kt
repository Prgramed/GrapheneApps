package dev.ecalendar.caldav

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

/**
 * OkHttp interceptor implementing a rolling-window rate limiter for Zoho CalDAV.
 * Zoho allows ~500 requests/hour per account. Uses 490 as threshold to leave headroom.
 * Each Zoho account should have its own interceptor instance.
 */
class ZohoRateLimitInterceptor : Interceptor {

    private val requestLog = ArrayDeque<Long>()
    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val windowStart = now - WINDOW_MS

            // Prune entries older than the window
            while (requestLog.isNotEmpty() && requestLog.first() < windowStart) {
                requestLog.removeFirst()
            }

            if (requestLog.size >= MAX_REQUESTS) {
                val oldestInWindow = requestLog.first()
                val waitMs = (oldestInWindow + WINDOW_MS) - now + 1000L
                Timber.w("Zoho rate limit: ${requestLog.size}/$MAX_REQUESTS requests in window, pausing ${waitMs / 1000}s")
                Thread.sleep(waitMs.coerceAtLeast(100L))
                // Re-prune after wait
                val afterWait = System.currentTimeMillis()
                while (requestLog.isNotEmpty() && requestLog.first() < afterWait - WINDOW_MS) {
                    requestLog.removeFirst()
                }
            }

            requestLog.addLast(System.currentTimeMillis())
        }

        return chain.proceed(chain.request())
    }

    companion object {
        private const val MAX_REQUESTS = 490 // 500 limit with 10 headroom
        private const val WINDOW_MS = 3_600_000L // 1 hour
    }
}

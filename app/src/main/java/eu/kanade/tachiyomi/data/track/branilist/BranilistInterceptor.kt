package eu.kanade.tachiyomi.data.track.branilist

import okhttp3.Interceptor
import okhttp3.Response

// KMK -->
internal class BranilistInterceptor(
    private val tracker: Branilist,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tracker.loadOAuth()?.accessToken
        val request = chain.request().newBuilder()
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()
        val response = chain.proceed(request)
        if (response.code != 401) return response
        response.close()
        synchronized(this) {
            val current = tracker.loadOAuth() ?: return chain.proceed(request)
            if (current.accessToken == token) {
                tracker.saveOAuth(tracker.apiRefreshToken(current))
            }
            val refreshed = tracker.loadOAuth()?.accessToken ?: return chain.proceed(request)
            return chain.proceed(request.newBuilder().header("Authorization", "Bearer $refreshed").build())
        }
    }
}
// KMK <--

package eu.kanade.tachiyomi.data.track.branilist

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.io.IOException

// KMK -->
class BranilistApi(
    private val client: OkHttpClient,
    private val tracker: Branilist,
) {
    private val json: Json by injectLazy()
    private val authClient by lazy { client.newBuilder().addInterceptor(BranilistInterceptor(tracker)).build() }

    suspend fun exchangeCode(code: String, verifier: String): BranilistOAuth = withIOContext {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", CLIENT_ID)
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("code_verifier", verifier)
            .build()
        with(json) {
            client.newCall(POST(TOKEN_URL, body = body)).awaitSuccess().parseAs<BranilistOAuth>().withExpiry()
        }
    }

    fun refreshToken(oauth: BranilistOAuth): BranilistOAuth {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", CLIENT_ID)
            .add("refresh_token", oauth.refreshToken)
            .build()
        return client.newCall(POST(TOKEN_URL, body = body)).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Branilist token refresh failed (${response.code})")
            json.decodeFromString(BranilistOAuth.serializer(), response.body!!.string()).withExpiry()
        }
    }

    suspend fun currentUser(): BranilistUser = withIOContext {
        with(json) { authClient.newCall(GET("$API_URL/api/v1/me")).awaitSuccess().parseAs<BranilistUser>() }
    }

    suspend fun search(query: String, trackerId: Long): List<TrackSearch> = withIOContext {
        val body = json.encodeToString(
            BranilistGraphQLSearchRequest(
                query = SEARCH_MEDIA_QUERY,
                variables = BranilistGraphQLSearchVariables(query = query, limit = SEARCH_LIMIT),
            ),
        ).toRequestBody(jsonMime)
        val response = with(json) {
            client.newCall(POST("$API_URL/graphql", body = body)).awaitSuccess().parseAs<BranilistGraphQLSearchResponse>()
        }
        response.errors.firstOrNull()?.let { throw IOException("Branilist search failed: ${it.message}") }
        response.data?.searchMedia.orEmpty()
            .filter { it.type == "MANGA" }
            .map { BranilistMapper.toTrackSearch(it, trackerId) }
    }

    suspend fun findLibraryEntry(mediaId: Long): BranilistLibraryEntry? = withIOContext {
        with(json) {
            authClient.newCall(GET("$API_URL/api/v1/me/library")).awaitSuccess()
                .parseAs<BranilistLibraryResponse>().items.firstOrNull { it.mediaId == mediaId }
        }
    }

    suspend fun deleteLibraryEntry(mediaId: Long) = withIOContext {
        val requestBody = json.encodeToString(
            BranilistGraphQLRemoveRequest(
                query = "mutation RemoveMediaEntry(${ '$' }mediaId: ID!) { removeMediaEntry(mediaId: ${ '$' }mediaId) }",
                variables = BranilistGraphQLRemoveVariables(mediaId.toString()),
            ),
        ).toRequestBody(jsonMime)
        val response = with(json) {
            authClient.newCall(POST("$API_URL/graphql", body = requestBody))
                .awaitSuccess().parseAs<BranilistGraphQLRemoveResponse>()
        }
        response.errors.firstOrNull()?.let { throw IOException("Branilist library removal failed: ${it.message}") }
        if (response.data?.removeMediaEntry != true) {
            throw IOException("Branilist did not remove the library entry")
        }
    }

    suspend fun updateLibrary(track: Track, repeatCount: Int): Track = withIOContext {
        val update = BranilistLibraryUpdate(
            status = BranilistMapper.statusToRemote(track.status),
            progress = track.last_chapter_read.toInt().coerceAtLeast(0),
            score = track.score.takeIf { it >= 0.5 },
            repeatCount = repeatCount.coerceAtLeast(0),
            startedAt = BranilistMapper.dateToRemote(track.started_reading_date),
            completedAt = BranilistMapper.dateToRemote(track.finished_reading_date),
            isPrivate = track.private,
        )
        val request = Request.Builder()
            .url("$API_URL/api/v1/me/library/${track.remote_id}")
            .put(json.encodeToString(update).toRequestBody(jsonMime))
            .build()
        authClient.newCall(request).awaitSuccess().close()
        track
    }

    private fun BranilistOAuth.withExpiry() = copy(
        expiresAt = System.currentTimeMillis() + (expiresIn - 60).coerceAtLeast(0) * 1000,
    )

    companion object {
        const val CLIENT_ID = "brn_y1Z-brIeBkc0zmtCDEIWjulH"
        const val REDIRECT_URI = "http://127.0.0.1:3000/oauth/callback"
        const val APP_CALLBACK_SCHEME = "komikku-branilist"
        const val APP_CALLBACK_HOST = "branilist-auth"
        // Branilist's production API is reverse-proxied on the main domain.
        private const val API_URL = "https://branilist.com"
        private const val AUTHORIZE_URL = "https://branilist.com/oauth/authorize"
        private const val TOKEN_URL = "$API_URL/oauth/token"
        private const val SEARCH_LIMIT = 30
        private const val SEARCH_MEDIA_QUERY = """
            query BranilistTrackerSearch(${ '$' }query: String!, ${ '$' }limit: Int!) {
              searchMedia(query: ${ '$' }query, limit: ${ '$' }limit) {
                id slug type status format
                title { romaji english native portuguese }
                description descriptionEnglish coverImage chapters averageScore
                startDate endDate
              }
            }
        """

        fun authUrl(state: String, challenge: String): Uri = AUTHORIZE_URL.toUri().buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", "profile:read library:read library:write")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()
    }
}

@Serializable
private data class BranilistLibraryUpdate(
    val status: String,
    val progress: Int,
    val score: Double?,
    val repeatCount: Int,
    val startedAt: String?,
    val completedAt: String?,
    @kotlinx.serialization.SerialName("private") val isPrivate: Boolean,
)
// KMK <--

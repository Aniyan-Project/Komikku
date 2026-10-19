package eu.kanade.tachiyomi.data.track.branilist

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import exh.log.xLogE
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.DecimalFormat
import java.util.Base64
import tachiyomi.domain.track.model.Track as DomainTrack

// KMK -->
class Branilist(id: Long) : BaseTracker(id, "Branilist") {
    private val json: Json by injectLazy()
    private val api by lazy { BranilistApi(client, this) }
    private val loginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getLogo() = R.drawable.brand_branilist

    override fun getStatusList() = listOf(
        BranilistMapper.READING,
        BranilistMapper.COMPLETED,
        BranilistMapper.ON_HOLD,
        BranilistMapper.DROPPED,
        BranilistMapper.PLANNING,
    )

    override fun getStatus(status: Long): StringResource? = when (status) {
        BranilistMapper.READING -> MR.strings.reading
        BranilistMapper.COMPLETED -> MR.strings.completed
        BranilistMapper.ON_HOLD -> MR.strings.on_hold
        BranilistMapper.DROPPED -> MR.strings.dropped
        BranilistMapper.PLANNING -> MR.strings.plan_to_read
        else -> null
    }

    override fun getReadingStatus() = BranilistMapper.READING
    override fun getRereadingStatus() = -1L
    override fun getCompletionStatus() = BranilistMapper.COMPLETED
    override fun getScoreList(): ImmutableList<String> {
        val format = DecimalFormat("0.#")
        return (listOf("0") + (1..20).map { format.format(it / 2.0) }).toImmutableList()
    }

    override fun indexToScore(index: Int) = if (index > 0) index / 2.0 else 0.0
    override fun displayScore(track: DomainTrack) = DecimalFormat("0.#").format(track.score)

    override suspend fun update(track: Track, didReadChapter: Boolean): Track = withIOContext {
        val entry = api.findLibraryEntry(track.remote_id)
        api.updateLibrary(track, entry?.repeatCount ?: 0)
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val entry = api.findLibraryEntry(track.remote_id)
        return if (entry != null) {
            track.library_id = entry.mediaId
            track.status = BranilistMapper.statusFromRemote(entry.status)
            track.last_chapter_read = entry.progress.toDouble()
            track.score = entry.score ?: 0.0
            track
        } else {
            if (hasReadChapters) track.status = BranilistMapper.READING
            api.updateLibrary(track, 0)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> = api.search(query, id)

    override suspend fun refresh(track: Track): Track = withIOContext {
        val entry = api.findLibraryEntry(track.remote_id) ?: return@withIOContext track
        track.status = BranilistMapper.statusFromRemote(entry.status)
        track.last_chapter_read = entry.progress.toDouble()
        track.score = entry.score ?: 0.0
        track.total_chapters = entry.media?.chapters?.toLong() ?: track.total_chapters
        track
    }

    override suspend fun login(username: String, password: String) = Unit

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        trackPreferences.branilistPendingOAuth().delete()
    }

    override fun hasNotStartedReading(status: Long) = status == BranilistMapper.PLANNING

    suspend fun beginLogin(): String {
        val state = randomToken()
        val verifier = randomToken()
        val challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val server = withContext(Dispatchers.IO) { BranilistOAuthCallbackServer.open() }
        trackPreferences.branilistPendingOAuth().set(json.encodeToString(PendingOAuth.serializer(), PendingOAuth(state, verifier)))
        loginScope.launch {
            try {
                BranilistOAuthCallbackServer.awaitAuthorization(server, ::completeLogin)
            } catch (error: SocketTimeoutException) {
                clearPendingLogin(state)
                xLogE("Branilist OAuth callback timed out", error)
            } catch (error: Exception) {
                clearPendingLogin(state)
                xLogE("Branilist OAuth callback or token exchange failed", error)
            }
        }
        return BranilistApi.authUrl(state, challenge).toString()
    }

    suspend fun completeLogin(state: String?, code: String?) {
        val pending = runCatching {
            json.decodeFromString(PendingOAuth.serializer(), trackPreferences.branilistPendingOAuth().get())
        }.getOrNull() ?: throw IllegalArgumentException("OAuth login request expired")
        require(!state.isNullOrBlank() && pending.state == state) { "OAuth state validation failed" }
        trackPreferences.branilistPendingOAuth().delete()
        val verifier = pending.verifier
        require(!code.isNullOrBlank()) { "Branilist authorization was cancelled" }
        val oauth = api.exchangeCode(code, verifier)
        saveOAuth(oauth)
        try {
            val user = api.currentUser()
            saveCredentials(user.username, user.id.toString())
        } catch (error: Throwable) {
            logout()
            throw error
        }
    }

    fun saveOAuth(oauth: BranilistOAuth) {
        trackPreferences.trackToken(this).set(json.encodeToString(BranilistOAuth.serializer(), oauth))
    }

    fun loadOAuth(): BranilistOAuth? = runCatching {
        json.decodeFromString(BranilistOAuth.serializer(), trackPreferences.trackToken(this).get())
    }.getOrNull()

    internal fun apiRefreshToken(oauth: BranilistOAuth): BranilistOAuth = api.refreshToken(oauth).also(::saveOAuth)

    private fun clearPendingLogin(state: String) {
        val pending = runCatching {
            json.decodeFromString(PendingOAuth.serializer(), trackPreferences.branilistPendingOAuth().get())
        }.getOrNull()
        if (pending?.state == state) trackPreferences.branilistPendingOAuth().delete()
    }

    private fun randomToken(): String = ByteArray(32).also(SecureRandom()::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
}

@kotlinx.serialization.Serializable
private data class PendingOAuth(val state: String, val verifier: String)
// KMK <--

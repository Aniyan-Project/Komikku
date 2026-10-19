package eu.kanade.tachiyomi.data.track.branilist

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// KMK -->
@Serializable
data class BranilistTitle(
    val romaji: String? = null,
    val english: String? = null,
    val portuguese: String? = null,
)

@Serializable
data class BranilistMedia(
    val id: Long,
    val slug: String,
    val type: String,
    val status: String = "",
    val format: String = "",
    val title: BranilistTitle = BranilistTitle(),
    val description: String? = null,
    val coverImage: String? = null,
    val chapters: Int? = null,
    val averageScore: Int? = null,
)

@Serializable
data class BranilistSearchResponse(val items: List<BranilistMedia> = emptyList())

@Serializable
data class BranilistLibraryEntry(
    val mediaId: Long,
    val status: String,
    val progress: Int = 0,
    val score: Double? = null,
    val repeatCount: Int = 0,
    val media: BranilistMedia? = null,
)

@Serializable
data class BranilistLibraryResponse(val items: List<BranilistLibraryEntry> = emptyList())

@Serializable
data class BranilistUser(
    val id: Long,
    val username: String,
)

@Serializable
data class BranilistOAuth(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long,
    val scope: String = "",
    val expiresAt: Long = 0,
)

internal object BranilistMapper {
    const val PLANNING = 1L
    const val READING = 2L
    const val COMPLETED = 3L
    const val ON_HOLD = 4L
    const val DROPPED = 5L

    fun statusFromRemote(status: String): Long = when (status.uppercase()) {
        "CURRENT" -> READING
        "COMPLETED" -> COMPLETED
        "PAUSED" -> ON_HOLD
        "DROPPED" -> DROPPED
        else -> PLANNING
    }

    fun statusToRemote(status: Long): String = when (status) {
        READING -> "CURRENT"
        COMPLETED -> "COMPLETED"
        ON_HOLD -> "PAUSED"
        DROPPED -> "DROPPED"
        else -> "PLANNING"
    }

    fun toTrackSearch(media: BranilistMedia, trackerId: Long): TrackSearch = TrackSearch.create(trackerId).apply {
        remote_id = media.id
        title = media.title.portuguese?.takeIf(String::isNotBlank)
            ?: media.title.english?.takeIf(String::isNotBlank)
            ?: media.title.romaji.orEmpty()
        tracking_url = "https://branilist.com/manga/${media.id}/${media.slug}"
        cover_url = media.coverImage.orEmpty()
        summary = media.description.orEmpty()
        publishing_status = media.status
        publishing_type = media.format
        total_chapters = media.chapters?.toLong() ?: 0L
    }
}
// KMK <--

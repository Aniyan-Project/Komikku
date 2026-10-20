package eu.kanade.tachiyomi.data.track.branilist

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// KMK -->
@Serializable
internal data class BranilistTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
    val portuguese: String? = null,
)

@Serializable
internal data class BranilistMedia(
    val id: Long,
    val slug: String,
    val type: String,
    val status: String = "",
    val format: String = "",
    val title: BranilistTitle = BranilistTitle(),
    val description: String? = null,
    val descriptionEnglish: String? = null,
    val coverImage: String? = null,
    val chapters: Int? = null,
    val averageScore: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
)

@Serializable
internal data class BranilistGraphQLSearchRequest(
    val query: String,
    val variables: BranilistGraphQLSearchVariables,
)

@Serializable
internal data class BranilistGraphQLSearchVariables(val query: String, val limit: Int)

@Serializable
internal data class BranilistGraphQLSearchResponse(
    val data: BranilistGraphQLSearchData? = null,
    val errors: List<BranilistGraphQLError> = emptyList(),
)

@Serializable
internal data class BranilistGraphQLSearchData(val searchMedia: List<BranilistMedia> = emptyList())

@Serializable
internal data class BranilistGraphQLRemoveRequest(
    val query: String,
    val variables: BranilistGraphQLRemoveVariables,
)

@Serializable
internal data class BranilistGraphQLRemoveVariables(val mediaId: String)

@Serializable
internal data class BranilistGraphQLRemoveResponse(
    val data: BranilistGraphQLRemoveData? = null,
    val errors: List<BranilistGraphQLError> = emptyList(),
)

@Serializable
internal data class BranilistGraphQLRemoveData(val removeMediaEntry: Boolean? = null)

@Serializable
internal data class BranilistGraphQLError(val message: String)

@Serializable
internal data class BranilistLibraryEntry(
    val mediaId: Long,
    val status: String,
    val progress: Int = 0,
    val score: Double? = null,
    val repeatCount: Int = 0,
    val startedAt: String? = null,
    val completedAt: String? = null,
    @SerialName("private") val isPrivate: Boolean = false,
    val media: BranilistMedia? = null,
)

@Serializable
internal data class BranilistLibraryResponse(val items: List<BranilistLibraryEntry> = emptyList())

@Serializable
internal data class BranilistUser(
    val id: Long,
    val username: String,
)

@Serializable
internal data class BranilistOAuth(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long,
    val scope: String = "",
    val expiresAt: Long = 0,
)

internal object BranilistMapper {
    const val INITIAL_SCORE = 0.0
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

    fun statusAfterProgress(status: Long, progress: Double, didReadChapter: Boolean): Long =
        if (status == PLANNING && (progress > 0 || didReadChapter)) READING else status

    fun initialStatus(hasReadChapters: Boolean): Long = if (hasReadChapters) READING else PLANNING

    fun dateFromRemote(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val date = runCatching { LocalDate.parse(value.take(10)) }.getOrElse {
            runCatching { Instant.parse(value).atZone(ZoneOffset.UTC).toLocalDate() }.getOrNull() ?: return 0L
        }
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    fun dateToRemote(value: Long): String? = if (value <= 0) {
        null
    } else {
        Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate().toString()
    }

    fun toTrackSearch(media: BranilistMedia, trackerId: Long): TrackSearch = TrackSearch.create(trackerId).apply {
        remote_id = media.id
        title = media.title.romaji?.takeIf(String::isNotBlank)
            ?: media.title.english?.takeIf(String::isNotBlank)
            ?: media.title.portuguese?.takeIf(String::isNotBlank)
            ?: media.title.native.orEmpty()
        tracking_url = "https://branilist.com/manga/${media.id}/${media.slug}"
        cover_url = media.coverImage.orEmpty()
        summary = media.descriptionEnglish?.takeIf(String::isNotBlank) ?: media.description.orEmpty()
        publishing_status = media.status
        publishing_type = media.format
        total_chapters = media.chapters?.toLong() ?: 0L
        score = media.averageScore?.toDouble() ?: 0.0
        start_date = media.startDate?.take(10).orEmpty()
    }
}
// KMK <--

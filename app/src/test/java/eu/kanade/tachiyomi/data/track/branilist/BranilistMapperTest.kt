package eu.kanade.tachiyomi.data.track.branilist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK -->
class BranilistMapperTest {
    @Test
    fun `remote statuses map to tracker statuses`() {
        assertEquals(BranilistMapper.PLANNING, BranilistMapper.statusFromRemote("PLANNING"))
        assertEquals(BranilistMapper.READING, BranilistMapper.statusFromRemote("CURRENT"))
        assertEquals(BranilistMapper.COMPLETED, BranilistMapper.statusFromRemote("COMPLETED"))
        assertEquals(BranilistMapper.ON_HOLD, BranilistMapper.statusFromRemote("PAUSED"))
        assertEquals(BranilistMapper.DROPPED, BranilistMapper.statusFromRemote("DROPPED"))
    }

    @Test
    fun `tracker statuses map to Branilist statuses`() {
        assertEquals("PLANNING", BranilistMapper.statusToRemote(BranilistMapper.PLANNING))
        assertEquals("CURRENT", BranilistMapper.statusToRemote(BranilistMapper.READING))
        assertEquals("COMPLETED", BranilistMapper.statusToRemote(BranilistMapper.COMPLETED))
        assertEquals("PAUSED", BranilistMapper.statusToRemote(BranilistMapper.ON_HOLD))
        assertEquals("DROPPED", BranilistMapper.statusToRemote(BranilistMapper.DROPPED))
    }

    @Test
    fun `new entries get a planned status unless chapters were already read`() {
        assertEquals(BranilistMapper.PLANNING, BranilistMapper.initialStatus(hasReadChapters = false))
        assertEquals(BranilistMapper.READING, BranilistMapper.initialStatus(hasReadChapters = true))
        assertEquals(0.0, BranilistMapper.INITIAL_SCORE)
    }

    @Test
    fun `reading progress moves planned entries to current`() {
        assertEquals(BranilistMapper.READING, BranilistMapper.statusAfterProgress(BranilistMapper.PLANNING, 1.0, false))
        assertEquals(BranilistMapper.READING, BranilistMapper.statusAfterProgress(BranilistMapper.PLANNING, 0.0, true))
        assertEquals(BranilistMapper.PLANNING, BranilistMapper.statusAfterProgress(BranilistMapper.PLANNING, 0.0, false))
        assertEquals(BranilistMapper.ON_HOLD, BranilistMapper.statusAfterProgress(BranilistMapper.ON_HOLD, 2.0, true))
    }

    @Test
    fun `search result prefers romaji title and preserves Branilist media link`() {
        val media = BranilistMedia(
            id = 42,
            slug = "sample-manga",
            type = "MANGA",
            title = BranilistTitle(romaji = "Romanji", english = "English", portuguese = "Exemplo", native = "原題"),
            coverImage = "https://cdn.branilist.com/cover.jpg",
        )

        val result = BranilistMapper.toTrackSearch(media, trackerId = 10)

        assertEquals(42, result.remote_id)
        assertEquals("Romanji", result.title)
        assertEquals("https://branilist.com/manga/42/sample-manga", result.tracking_url)
        assertEquals("https://cdn.branilist.com/cover.jpg", result.cover_url)
    }

    @Test
    fun `search result falls back to English before Portuguese title`() {
        val media = BranilistMedia(
            id = 45,
            slug = "romaji-fallback",
            type = "MANGA",
            title = BranilistTitle(english = "English", portuguese = "Exemplo", native = "原題"),
        )

        assertEquals("English", BranilistMapper.toTrackSearch(media, trackerId = 10).title)
    }

    @Test
    fun `search result falls back to Portuguese before native title`() {
        val media = BranilistMedia(
            id = 46,
            slug = "Portuguese-fallback",
            type = "MANGA",
            title = BranilistTitle(portuguese = "Exemplo", native = "原題"),
        )

        assertEquals("Exemplo", BranilistMapper.toTrackSearch(media, trackerId = 10).title)
    }

    @Test
    fun `search result uses native title when localized titles are unavailable`() {
        val media = BranilistMedia(
            id = 43,
            slug = "native-title",
            type = "MANGA",
            title = BranilistTitle(native = "原題"),
        )

        assertEquals("原題", BranilistMapper.toTrackSearch(media, trackerId = 10).title)
    }

    @Test
    fun `search result includes English synopsis release date and rating`() {
        val media = BranilistMedia(
            id = 44,
            slug = "metadata-manga",
            type = "MANGA",
            description = "Resumo em português",
            descriptionEnglish = "English synopsis",
            averageScore = 64,
            startDate = "2025-01-25T00:00:00Z",
        )

        val result = BranilistMapper.toTrackSearch(media, trackerId = 10)

        assertEquals("English synopsis", result.summary)
        assertEquals("2025-01-25", result.start_date)
        assertEquals(64.0, result.score)
    }

    @Test
    fun `tracking dates round trip as UTC calendar dates`() {
        val startedAt = BranilistMapper.dateFromRemote("2025-01-25T00:00:00Z")

        assertEquals("2025-01-25", BranilistMapper.dateToRemote(startedAt))
        assertEquals(0L, BranilistMapper.dateFromRemote(null))
        assertEquals(null, BranilistMapper.dateToRemote(0L))
    }
}
// KMK <--

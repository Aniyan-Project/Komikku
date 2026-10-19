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
    fun `search result preserves Branilist media link and title`() {
        val media = BranilistMedia(
            id = 42,
            slug = "sample-manga",
            type = "MANGA",
            title = BranilistTitle(romaji = "Sample", portuguese = "Exemplo"),
            coverImage = "https://cdn.branilist.com/cover.jpg",
        )

        val result = BranilistMapper.toTrackSearch(media, trackerId = 10)

        assertEquals(42, result.remote_id)
        assertEquals("Exemplo", result.title)
        assertEquals("https://branilist.com/manga/42/sample-manga", result.tracking_url)
        assertEquals("https://cdn.branilist.com/cover.jpg", result.cover_url)
    }
}
// KMK <--

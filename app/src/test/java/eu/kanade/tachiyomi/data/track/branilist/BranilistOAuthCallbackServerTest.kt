package eu.kanade.tachiyomi.data.track.branilist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class BranilistOAuthCallbackServerTest {
    @Test
    fun `callback target reads code and state`() {
        val callback = BranilistOAuthCallbackServer.parseCallbackTarget("/oauth/callback?code=brc_test&state=state_test")

        assertEquals("brc_test", callback?.code)
        assertEquals("state_test", callback?.state)
        assertEquals(null, callback?.error)
    }

    @Test
    fun `callback target rejects other paths`() {
        assertEquals(null, BranilistOAuthCallbackServer.parseCallbackTarget("/other?code=brc_test"))
    }

    @Test
    fun `callback server binds only to IPv4 loopback`() {
        BranilistOAuthCallbackServer.open(port = 0).use { server ->
            assertTrue(server.inetAddress.isLoopbackAddress)
            assertEquals("127.0.0.1", server.inetAddress.hostAddress)
        }
    }
}
// KMK <--

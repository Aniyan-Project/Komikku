package eu.kanade.tachiyomi.data.track.branilist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class BranilistOAuthCallbackServerTest {
    @Test
    fun `callback server binds only to IPv4 loopback`() {
        BranilistOAuthCallbackServer.open(port = 0).use { server ->
            assertTrue(server.inetAddress.isLoopbackAddress)
            assertEquals("127.0.0.1", server.inetAddress.hostAddress)
        }
    }
}
// KMK <--

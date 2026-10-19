package eu.kanade.tachiyomi.data.track.branilist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI

// KMK -->
internal object BranilistOAuthCallbackServer {
    private var activeServer: ServerSocket? = null

    @Synchronized
    fun open(port: Int = CALLBACK_PORT): ServerSocket {
        activeServer?.close()
        return ServerSocket(port, 1, InetAddress.getByName(CALLBACK_HOST)).apply {
            soTimeout = 180_000
            activeServer = this
        }
    }

    suspend fun awaitAuthorization(server: ServerSocket) = withContext(Dispatchers.IO) {
        try {
            server.use {
                it.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    val requestLine = input.readLine().orEmpty()
                    while (input.readLine()?.isNotEmpty() == true) Unit

                    val path = requestLine.split(' ').getOrNull(1).orEmpty()
                    val uri = runCatching { URI("http://$CALLBACK_HOST:$CALLBACK_PORT$path") }.getOrNull()
                    val location = if (uri?.path == "/oauth/callback") {
                        "${BranilistApi.APP_CALLBACK_SCHEME}://${BranilistApi.APP_CALLBACK_HOST}${uri.rawQuery?.let { "?$it" }.orEmpty()}"
                    } else {
                        "${BranilistApi.APP_CALLBACK_SCHEME}://${BranilistApi.APP_CALLBACK_HOST}?error=invalid_callback"
                    }

                    val output = socket.getOutputStream().bufferedWriter()
                    output.write("HTTP/1.1 302 Found\r\nLocation: $location\r\nConnection: close\r\nContent-Length: 0\r\n\r\n")
                    output.flush()
                }
            }
        } finally {
            synchronized(this@BranilistOAuthCallbackServer) {
                if (activeServer === server) activeServer = null
            }
        }
    }

    private const val CALLBACK_HOST = "127.0.0.1"
    private const val CALLBACK_PORT = 3000
}
// KMK <--

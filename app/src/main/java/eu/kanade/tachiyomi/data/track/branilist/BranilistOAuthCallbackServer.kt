package eu.kanade.tachiyomi.data.track.branilist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// KMK -->
internal data class BranilistOAuthCallback(
    val state: String?,
    val code: String?,
    val error: String?,
)

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

    suspend fun awaitAuthorization(
        server: ServerSocket,
        onAuthorization: suspend (state: String?, code: String?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        var callbackError: Exception? = null
        try {
            server.use {
                it.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    val requestLine = input.readLine().orEmpty()
                    var header = input.readLine()
                    while (!header.isNullOrEmpty()) {
                        header = input.readLine()
                    }

                    val path = requestLine.split(' ').getOrNull(1).orEmpty()
                    val callback = parseCallbackTarget(path)
                    val result = when {
                        callback == null -> "error=invalid_callback"
                        callback.error != null -> "error=authorization_denied"
                        else -> {
                            try {
                                onAuthorization(callback.state, callback.code)
                                "result=complete"
                            } catch (error: Exception) {
                                callbackError = error
                                "error=connection_failed"
                            }
                        }
                    }
                    val location = "${BranilistApi.APP_CALLBACK_SCHEME}://${BranilistApi.APP_CALLBACK_HOST}?$result"

                    val output = socket.getOutputStream().bufferedWriter()
                    output.write("HTTP/1.1 302 Found\r\nLocation: $location\r\nConnection: close\r\nContent-Length: 0\r\n\r\n")
                    output.flush()
                }
            }
            callbackError?.let { throw it }
        } finally {
            synchronized(this@BranilistOAuthCallbackServer) {
                if (activeServer === server) activeServer = null
            }
        }
    }

    internal fun parseCallbackTarget(target: String): BranilistOAuthCallback? {
        val uri = runCatching { URI("http://$CALLBACK_HOST:$CALLBACK_PORT$target") }.getOrNull()
            ?: return null
        if (uri.path != "/oauth/callback") return null

        val query = uri.rawQuery.orEmpty().split('&')
            .filter(String::isNotBlank)
            .associate { part ->
                val (key, value) = part.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                URLDecoder.decode(key, StandardCharsets.UTF_8) to URLDecoder.decode(value, StandardCharsets.UTF_8)
            }
        return BranilistOAuthCallback(query["state"], query["code"], query["error"])
    }

    private const val CALLBACK_HOST = "127.0.0.1"
    private const val CALLBACK_PORT = 3000
}
// KMK <--

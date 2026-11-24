@file:OptIn(ExperimentalForeignApi::class)

package reverie.egoland

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import platform.posix.SIGINT
import platform.posix.SIGTERM
import platform.posix.signal

private val signalHandler = staticCFunction<Int, Unit> { _ ->
    wlr.wl_display_terminate(serverInstance?.display)
}

private var serverInstance: Server? = null

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    val server = Server()
    serverInstance = server
    if (!server.start()) {
        server.cleanup()
        return
    }

    signal(SIGINT, signalHandler)
    signal(SIGTERM, signalHandler)

    try {
        server.run()
    } finally {
        server.cleanup()
        serverInstance = null
    }
}

@file:OptIn(ExperimentalForeignApi::class)

package reverie.egoland

import kotlinx.cinterop.*
import platform.posix.setenv
import wlr.*

class XWaylandManager(val server: Server) {
    private var xwayland: CPointer<wlr_xwayland>? = null
    private val newSurfaceListener = SignalListener()
    private val readyListener = SignalListener()

    fun init() {
        val xw = wlr_xwayland_create(server.display, server.compositor, false) ?: run {
            println("egoland: warning: XWayland unavailable")
            return
        }
        xwayland = xw
        wlr_xwayland_set_seat(xw, server.seat)

        readyListener.onSignal = { _, _ ->
            val displayName = wlr_shim_xwayland_display_name(xw)?.toKString()
            if (displayName != null) {
                setenv("DISPLAY", displayName, 1)
                println("egoland: XWayland DISPLAY=$displayName")
            }
        }
        readyListener.connect(wlr_shim_xwayland_ready(xw)!!)

        newSurfaceListener.onSignal = { _, data -> onNewSurface(data!!) }
        newSurfaceListener.connect(wlr_shim_xwayland_new_surface(xw)!!)
    }

    private fun onNewSurface(data: COpaquePointer) {
        val xwSurface = data.reinterpret<wlr_xwayland_surface>()
        if (wlr_shim_xwayland_surface_override_redirect(xwSurface) != 0) return
        val surface = wlr_shim_xwayland_surface_get_surface(xwSurface) ?: return

        val tree = wlr_scene_tree_create(wlr_shim_scene_root_tree(server.scene)) ?: return
        val contentTree = wlr_scene_subsurface_tree_create(tree, surface) ?: run {
            wlr_scene_node_destroy(tree.pointed.node.ptr)
            return
        }

        val window = Window(surface, contentTree, tree.pointed.node.ptr)
        window.xwaylandSurface = xwSurface
        window.title = wlr_shim_xwayland_surface_title(xwSurface)?.toKString() ?: ""
        window.x = wlr_shim_xwayland_surface_x(xwSurface).toInt()
        window.y = wlr_shim_xwayland_surface_y(xwSurface).toInt()
        window.width = wlr_shim_xwayland_surface_width(xwSurface).toInt()
        window.height = wlr_shim_xwayland_surface_height(xwSurface).toInt()
        window.configureGeometry(window.x, window.y, window.width, window.height)
        wlr_scene_node_set_enabled(window.node, false)

        val ref = StableRef.create(window)
        window.stableRef = ref

        val mapListener = SignalListener()
        mapListener.onSignal = { _, _ ->
            window.mapped = true
            wlr_scene_node_set_enabled(window.node, true)
            server.windowManager.focus(window)
            println("egoland: X11 window mapped: ${window.title}")
        }
        mapListener.connect(wlr_shim_surface_map(surface)!!)

        val unmapListener = SignalListener()
        unmapListener.onSignal = { _, _ ->
            window.mapped = false
            wlr_scene_node_set_enabled(window.node, false)
        }
        unmapListener.connect(wlr_shim_surface_unmap(surface)!!)

        val destroyListener = SignalListener()
        destroyListener.onSignal = { _, _ ->
            window.listeners.forEach { it.disconnect() }
            ref.dispose()
            server.windowManager.windows.remove(window)
            if (server.windowManager.focused === window) {
                server.windowManager.clearFocus()
            }
        }
        destroyListener.connect(wlr_shim_xwayland_surface_destroy(xwSurface)!!)

        val activateListener = SignalListener()
        activateListener.onSignal = { _, _ -> server.windowManager.focus(window) }
        activateListener.connect(wlr_shim_xwayland_surface_request_activate(xwSurface)!!)

        window.listeners.addAll(listOf(mapListener, unmapListener, destroyListener, activateListener))
        server.windowManager.addWindow(window)
        println("egoland: new X11 surface: ${window.title}")
    }

    fun dispose() {
        newSurfaceListener.disconnect()
        readyListener.disconnect()
        xwayland?.let { wlr_xwayland_destroy(it) }
        xwayland = null
    }
}

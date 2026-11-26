@file:OptIn(ExperimentalForeignApi::class)

package reverie.egoland.protocol

import cnames.structs.wl_event_loop
import cnames.structs.wl_event_source
import kotlinx.cinterop.*
import reverie.egoland.HitResult
import reverie.egoland.Server
import reverie.egoland.SignalListener
import reverie.egoland.Window
import wlr.*

class Popup(
    val surface: CPointer<wlr_surface>,
    val xdgSurface: CPointer<wlr_xdg_surface>,
    val tree: CPointer<wlr_scene_tree>,
    val node: CPointer<wlr_scene_node>,
    val depth: Int
) {
    var mapped: Boolean = false
    var x: Int = 0
        private set
    var y: Int = 0
        private set
    var width: Int = 0
    var height: Int = 0
    var geomX: Int = 0
    var geomY: Int = 0

    fun updatePosition() {
        val position = sceneNodeAbsolutePosition(node)
        x = position.first
        y = position.second
    }

    fun containsPoint(px: Int, py: Int): Boolean {
        updatePosition()
        return px >= x && px < x + width && py >= y && py < y + height
    }
}

private class ConfigureIdleState(
    val surface: CPointer<wlr_xdg_surface>,
    var active: Boolean = true
)

private class ConfigureIdle(
    private val loop: CPointer<wl_event_loop>?,
    private val surface: CPointer<wlr_xdg_surface>
) {
    private var state: ConfigureIdleState? = null
    private var ref: StableRef<ConfigureIdleState>? = null
    private var source: CPointer<wl_event_source>? = null

    companion object {
        private val callback = staticCFunction<COpaquePointer?, Unit> { data ->
            val state = data!!.asStableRef<ConfigureIdleState>().get()
            if (state.active) {
                state.active = false
                wlr_shim_xdg_surface_schedule_configure_if_ready(state.surface)
            }
        }
    }

    fun schedule() {
        if (source != null) return
        val newState = ConfigureIdleState(surface)
        state = newState
        ref = StableRef.create(newState)
        source = wl_event_loop_add_idle(loop, callback, ref!!.asCPointer())
    }

    fun dispose() {
        if (state?.active == true) source?.let { wl_event_source_remove(it) }
        source = null
        state = null
        ref?.dispose()
        ref = null
    }
}

class WindowManager(val server: Server) {
    val windows = mutableListOf<Window>()
    val popups = mutableListOf<Popup>()
    var focused: Window? = null
        private set

    fun popupAt(px: Int, py: Int): Popup? {
        val hits = popups.filter { it.mapped && it.containsPoint(px, py) }
        val deepest = hits.maxOfOrNull { it.depth } ?: return null
        return hits.lastOrNull { it.depth == deepest }
    }

    private val newToplevelListener = SignalListener()
    private val decorationManager = XdgDecorationManager(server)

    fun init() {
        val shell = server.xdgShell ?: return
        newToplevelListener.onSignal = { _, data -> onNewXdgToplevel(data!!) }
        newToplevelListener.connect(wlr_shim_xdg_shell_new_toplevel(shell)!!)
        decorationManager.init()
    }

    private fun onNewXdgToplevel(data: COpaquePointer) {
        val xdgToplevel = data.reinterpret<wlr_xdg_toplevel>()
        val xdgSurface = wlr_shim_xdg_toplevel_base(xdgToplevel) ?: return
        val surface = wlr_shim_xdg_surface_get_surface(xdgSurface) ?: return
        if (wlr_shim_xdg_surface_role(xdgSurface) != wlr_xdg_surface_role.WLR_XDG_SURFACE_ROLE_TOPLEVEL.value) return

        val tree = wlr_scene_tree_create(wlr_shim_scene_root_tree(server.scene)) ?: return
        val contentTree = wlr_scene_xdg_surface_create(tree, xdgSurface) ?: run {
            wlr_scene_node_destroy(tree.pointed.node.ptr)
            return
        }

        val window = Window(surface, contentTree, tree.pointed.node.ptr)
        window.xdgSurface = xdgSurface
        window.title = wlr_shim_xdg_toplevel_title(xdgToplevel)?.toKString() ?: ""
        addDecoration(window)
        decorationManager.applyConfiguredMode(window)

        connectXdgListeners(window, xdgSurface, surface)
        windows.add(window)
        decorationManager.attach(window)
        println("egoland: new xdg toplevel: ${window.title}")
    }

    private fun addDecoration(window: Window) {
        val parent = window.contentTree
        val c = Window.INACTIVE_COLOR
        window.titleNode = wlr_shim_scene_rect_create(
            parent, 800, Window.DECOR_HEIGHT, c[0], c[1], c[2], c[3]
        )
        window.borderNode = wlr_shim_scene_rect_create(
            parent, 800, 1, c[0], c[1], c[2], c[3]
        )
        wlr_scene_node_set_position(window.titleNode!!.pointed.node.ptr, 0, -Window.DECOR_HEIGHT)
        wlr_scene_node_set_position(window.borderNode!!.pointed.node.ptr, 0, 0)
        wlr_scene_node_set_enabled(window.titleNode!!.pointed.node.ptr, false)
        wlr_scene_node_set_enabled(window.borderNode!!.pointed.node.ptr, false)
    }

    private fun updateWindowGeometry(window: Window, xdgSurface: CPointer<wlr_xdg_surface>) {
        memScoped {
            val gx = alloc<IntVar>()
            val gy = alloc<IntVar>()
            val gw = alloc<IntVar>()
            val gh = alloc<IntVar>()
            wlr_shim_xdg_surface_geometry(xdgSurface, gx.ptr, gy.ptr, gw.ptr, gh.ptr)
            window.geomX = gx.value
            window.geomY = gy.value
        }
    }

    private fun connectToplevelRequests(
        window: Window,
        xdgSurface: CPointer<wlr_xdg_surface>
    ): List<SignalListener> {
        val toplevel = wlr_shim_xdg_surface_toplevel(xdgSurface) ?: return emptyList()
        val moveListener = SignalListener()
        moveListener.onSignal = { _, _ -> server.inputManager.startMoveFromRequest(window) }
        val maximizeListener = SignalListener()
        maximizeListener.onSignal = { _, _ ->
            val requested = wlr_shim_xdg_toplevel_requested_maximized(toplevel)
            server.inputManager.setMaximizedFromRequest(window, requested != 0u)
        }
        val minimizeListener = SignalListener()
        minimizeListener.onSignal = { _, _ ->
            server.inputManager.setMinimizedFromRequest(window, true)
        }

        val selfRef = arrayOfNulls<SignalListener>(1)
        val destroyListener = SignalListener()
        selfRef[0] = destroyListener
        destroyListener.onSignal = { _, _ ->
            moveListener.disconnect()
            maximizeListener.disconnect()
            minimizeListener.disconnect()
            selfRef[0]?.disconnect()
        }

        destroyListener.connect(wlr_shim_xdg_toplevel_destroy(toplevel)!!)
        moveListener.connect(wlr_shim_xdg_toplevel_request_move(toplevel)!!)
        maximizeListener.connect(wlr_shim_xdg_toplevel_request_maximize(toplevel)!!)
        minimizeListener.connect(wlr_shim_xdg_toplevel_request_minimize(toplevel)!!)
        return listOf(destroyListener, moveListener, maximizeListener, minimizeListener)
    }

    private fun connectXdgListeners(
        window: Window,
        xdgSurface: CPointer<wlr_xdg_surface>,
        surface: CPointer<wlr_surface>
    ) {
        val configureIdle = ConfigureIdle(server.eventLoop, xdgSurface)
        var configured = false
        val commitListener = SignalListener()
        commitListener.onSignal = { _, _ ->
            updateWindowGeometry(window, xdgSurface)
            window.reapplyXdgState()
            if (!configured) {
                configured = true
                configureIdle.schedule()
            }
        }
        commitListener.connect(wlr_shim_surface_commit(surface)!!)

        val mapListener = SignalListener()
        mapListener.onSignal = { _, _ -> onMap(window) }
        mapListener.connect(wlr_shim_surface_map(surface)!!)

        val unmapListener = SignalListener()
        unmapListener.onSignal = { _, _ -> onUnmap(window) }
        unmapListener.connect(wlr_shim_surface_unmap(surface)!!)

        val destroyListener = SignalListener()
        destroyListener.onSignal = { _, _ ->
            configureIdle.dispose()
            onDestroy(window)
        }
        destroyListener.connect(wlr_shim_xdg_surface_destroy(xdgSurface)!!)

        val newPopupListener = SignalListener()
        newPopupListener.onSignal = { _, data -> onNewPopup(window, null, data!!) }
        newPopupListener.connect(wlr_shim_xdg_surface_new_popup(xdgSurface)!!)

        val requestListeners = connectToplevelRequests(window, xdgSurface)
        window.listeners.addAll(
            listOf(commitListener, mapListener, unmapListener, destroyListener, newPopupListener) +
                requestListeners
        )
    }

    private fun onNewPopup(window: Window, parent: Popup?, data: COpaquePointer) {
        val xdgPopup = data.reinterpret<wlr_xdg_popup>()
        val popupBase = wlr_shim_xdg_popup_base(xdgPopup) ?: return
        val popupSurface = wlr_shim_xdg_surface_get_surface(popupBase) ?: return
        val sceneParent = parent?.tree ?: window.contentTree
        val popupTree = wlr_scene_xdg_surface_create(sceneParent, popupBase) ?: return
        val depth = parent?.depth?.plus(1) ?: 0

        val popup = Popup(
            popupSurface, popupBase, popupTree, popupTree.pointed.node.ptr, depth
        )
        popups.add(popup)

        fun updateGeometry() {
            memScoped {
                val px = alloc<IntVar>()
                val py = alloc<IntVar>()
                val pw = alloc<IntVar>()
                val ph = alloc<IntVar>()
                wlr_shim_xdg_popup_geometry(
                    popupBase, px.ptr, py.ptr, pw.ptr, ph.ptr
                )
                popup.geomX = px.value
                popup.geomY = py.value
                popup.width = pw.value
                popup.height = ph.value
            }
            popup.updatePosition()
        }

        val configureIdle = ConfigureIdle(server.eventLoop, popupBase)
        var configured = false
        val commitListener = SignalListener()
        commitListener.onSignal = { _, _ ->
            updateGeometry()
            if (!configured) {
                configured = true
                configureIdle.schedule()
            }
        }
        commitListener.connect(wlr_shim_surface_commit(popupSurface)!!)

        val mapListener = SignalListener()
        mapListener.onSignal = { _, _ ->
            popup.mapped = true
            updateGeometry()
        }
        mapListener.connect(wlr_shim_surface_map(popupSurface)!!)

        val unmapListener = SignalListener()
        unmapListener.onSignal = { _, _ ->
            popup.mapped = false
            server.inputManager.onPopupUnmapped(popup)
        }
        unmapListener.connect(wlr_shim_surface_unmap(popupSurface)!!)

        val nestedPopupListener = SignalListener()
        nestedPopupListener.onSignal = { _, nestedData ->
            onNewPopup(window, popup, nestedData!!)
        }
        nestedPopupListener.connect(wlr_shim_xdg_surface_new_popup(popupBase)!!)

        val selfRef = arrayOfNulls<SignalListener>(1)
        val destroyListener = SignalListener()
        selfRef[0] = destroyListener
        destroyListener.onSignal = { _, _ ->
            configureIdle.dispose()
            commitListener.disconnect()
            mapListener.disconnect()
            unmapListener.disconnect()
            nestedPopupListener.disconnect()
            popups.remove(popup)
            server.inputManager.onPopupDestroyed(popup)
            selfRef[0]?.disconnect()
        }
        destroyListener.connect(wlr_shim_xdg_surface_destroy(popupBase)!!)
    }

    private fun onMap(window: Window) {
        window.mapped = true
        val r = (0..80).random()
        window.configureGeometry(60 + r, 60 + r, 900, 600)
        window.notifyGeometry()
        wlr_scene_node_set_enabled(window.node, true)
        focus(window)
        println("egoland: mapped ${window.title} at ${window.x},${window.y}")
    }

    private fun onUnmap(window: Window) {
        window.mapped = false
        wlr_scene_node_set_enabled(window.node, false)
        if (focused === window) focused = null
    }

    private fun onDestroy(window: Window) {
        server.dataDeviceManager.onWindowDestroyed(window)
        window.listeners.forEach { it.disconnect() }
        window.stableRef?.dispose()
        windows.remove(window)
        if (focused === window) focused = null
        println("egoland: destroyed window")
    }

    fun focus(window: Window) {
        if (focused === window) return
        focused?.activate(false)
        focused = window
        window.activate(true)
        window.toTop()
        windows.remove(window)
        windows.add(window)
        window.sendPing()
        val seat = server.seat ?: return
        wlr_shim_seat_keyboard_enter_with_modifiers(seat, window.surface)
    }

    fun addWindow(window: Window) {
        windows.add(window)
    }

    fun clearFocus() {
        focused?.activate(false)
        focused = null
    }

    fun topmostAt(px: Int, py: Int): Window? = windows.asReversed().firstOrNull {
        it.mapped && it.containsPoint(px, py)
    }

    fun dispose() {
        newToplevelListener.disconnect()
        decorationManager.dispose()
        windows.forEach {
            it.listeners.forEach { l -> l.disconnect() }
            it.stableRef?.dispose()
        }
        windows.clear()
    }
}

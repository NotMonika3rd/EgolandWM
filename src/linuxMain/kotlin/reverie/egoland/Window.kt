@file:OptIn(ExperimentalForeignApi::class)

package reverie.egoland

import kotlinx.cinterop.*
import wlr.*

class Window(
    val surface: CPointer<wlr_surface>,
    val contentTree: CPointer<wlr_scene_tree>,
    val node: CPointer<wlr_scene_node>
) {
    var x: Int = 0
    var y: Int = 0
    var width: Int = 800
    var height: Int = 600
    var mapped: Boolean = false
    var active: Boolean = false
    var title: String = ""
    var xdgSurface: CPointer<wlr_xdg_surface>? = null
    var xwaylandSurface: CPointer<wlr_xwayland_surface>? = null
    var geomX: Int = 0
    var geomY: Int = 0
    var maximized: Boolean = false
    var minimized: Boolean = false
    private var xdgStateApplied: Boolean = false
    var savedX: Int = 0
    var savedY: Int = 0
    var savedWidth: Int = 0
    var savedHeight: Int = 0

    var borderNode: CPointer<wlr_scene_rect>? = null
    var titleNode: CPointer<wlr_scene_rect>? = null
    var clientDecorated: Boolean = true
        private set

    val decorOffset: Int get() = if (clientDecorated) 0 else DECOR_HEIGHT
    val xdgReady: Boolean get() = xdgSurface?.let { wlr_shim_xdg_surface_initialized(it) != 0u } ?: false

    val listeners = mutableListOf<SignalListener>()
    var stableRef: StableRef<Window>? = null

    fun setClientDecorated(value: Boolean) {
        if (clientDecorated == value) return
        clientDecorated = value
        val enabled = !value
        titleNode?.let { wlr_scene_node_set_enabled(it.pointed.node.ptr, enabled) }
        borderNode?.let { wlr_scene_node_set_enabled(it.pointed.node.ptr, enabled) }
        configureGeometry(x, y, width, height)
    }

    fun configureGeometry(nx: Int, ny: Int, nw: Int, nh: Int) {
        x = nx
        y = ny
        width = nw.coerceAtLeast(100)
        height = nh.coerceAtLeast(100)
        wlr_scene_node_set_position(contentTree.pointed.node.ptr, x, y + decorOffset)
        borderNode?.let { wlr_scene_rect_set_size(it, width, 1) }
        titleNode?.let { wlr_scene_rect_set_size(it, width, DECOR_HEIGHT) }
    }

    fun saveGeometry() {
        savedX = x
        savedY = y
        savedWidth = width
        savedHeight = height
    }

    fun restoreGeometry() {
        configureGeometry(savedX, savedY, savedWidth, savedHeight)
    }

    fun reapplyXdgState() {
        val surface = xdgSurface ?: return
        if (xdgStateApplied || !xdgReady) return
        val toplevel = wlr_shim_xdg_surface_toplevel(surface) ?: return
        wlr_shim_xdg_toplevel_set_size(toplevel, width, height)
        wlr_xdg_toplevel_set_activated(toplevel, active)
        wlr_xdg_toplevel_set_maximized(toplevel, maximized)
        wlr_xdg_toplevel_set_suspended(toplevel, minimized)
        xdgStateApplied = true
    }

    fun notifyGeometry() {
        if (xdgReady) {
            xdgSurface?.let {
                wlr_shim_xdg_toplevel_set_size(wlr_shim_xdg_surface_toplevel(it), width, height)
            }
        }
        xwaylandSurface?.let {
            wlr_xwayland_surface_configure(
                it,
                x.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort(),
                (y + decorOffset).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort(),
                width.coerceAtMost(UShort.MAX_VALUE.toInt()).toUShort(),
                height.coerceAtMost(UShort.MAX_VALUE.toInt()).toUShort()
            )
        }
    }

    fun activate(active: Boolean) {
        this.active = active
        val c = if (active) ACTIVE_COLOR else INACTIVE_COLOR
        borderNode?.let { wlr_shim_scene_rect_set_color(it, c[0], c[1], c[2], c[3]) }
        titleNode?.let { wlr_shim_scene_rect_set_color(it, c[0], c[1], c[2], c[3]) }
        if (xdgReady) {
            xdgSurface?.let { wlr_xdg_toplevel_set_activated(wlr_shim_xdg_surface_toplevel(it), active) }
        }
        xwaylandSurface?.let { wlr_xwayland_surface_activate(it, active) }
    }

    fun close() {
        xdgSurface?.let { wlr_xdg_toplevel_send_close(wlr_shim_xdg_surface_toplevel(it)) }
        xwaylandSurface?.let { wlr_xwayland_surface_close(it) }
    }

    fun sendPing() {
        xdgSurface?.let { wlr_xdg_surface_ping(it) }
    }

    fun toTop() {
        wlr_scene_node_raise_to_top(node)
    }

    fun containsPoint(px: Int, py: Int): Boolean =
        px >= x && px < x + width && py >= y && py < y + height + decorOffset

    fun hitTest(px: Int, py: Int): HitResult {
        if (py < y) return HitResult.NONE
        val contentTop = y + decorOffset
        if (!clientDecorated && py < contentTop) {
            if (py < y + BORDER_WIDTH) {
                if (px < x + BORDER_WIDTH) return HitResult.RESIZE_NW
                if (px >= x + width - BORDER_WIDTH) return HitResult.RESIZE_NE
            }
            return HitResult.MOVE
        }
        val contentBottom = contentTop + height
        val contentMidY = contentTop + height / 2
        if (px < x + BORDER_WIDTH) {
            return if (py < contentMidY) HitResult.RESIZE_W else HitResult.RESIZE_SW
        }
        if (px >= x + width - BORDER_WIDTH) {
            return if (py < contentMidY) HitResult.RESIZE_E else HitResult.RESIZE_SE
        }
        if (py >= contentBottom - BORDER_WIDTH) {
            return if (px < x + width / 2) HitResult.RESIZE_S else HitResult.RESIZE_SE
        }
        return HitResult.CONTENT
    }

    companion object {
        const val DECOR_HEIGHT = 30
        const val BORDER_WIDTH = 6
        val ACTIVE_COLOR = floatArrayOf(0.3f, 0.5f, 0.8f, 1f)
        val INACTIVE_COLOR = floatArrayOf(0.2f, 0.2f, 0.2f, 1f)
    }
}

enum class HitResult {
    NONE, MOVE, CONTENT,
    RESIZE_N, RESIZE_S, RESIZE_E, RESIZE_W,
    RESIZE_NW, RESIZE_NE, RESIZE_SW, RESIZE_SE
}

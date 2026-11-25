@file:OptIn(ExperimentalForeignApi::class)

package reverie.egoland.protocol

import kotlinx.cinterop.*
import reverie.egoland.SignalListener
import reverie.egoland.Window
import reverie.egoland.HitResult
import reverie.egoland.Server
import wlr.*

sealed class Interaction {
    object None : Interaction()
    data class Move(
        val window: Window, val startX: Int, val startY: Int,
        val origX: Int, val origY: Int
    ) : Interaction()
    data class Resize(
        val window: Window, val edges: HitResult,
        val startX: Int, val startY: Int,
        val origX: Int, val origY: Int, val origW: Int, val origH: Int
    ) : Interaction()
}


private data class PointerTarget(
    val surface: CPointer<wlr_surface>,
    val sx: Double,
    val sy: Double,
    val popup: Popup?,
    val window: Window?
)

private data class KeyboardBinding(
    val keyboard: CPointer<wlr_keyboard>,
    val keyListener: SignalListener,
    val modifierListener: SignalListener
)

class InputManager(val server: Server) {
    private var cursor: CPointer<wlr_cursor>? = null
    private var xcursorManager: CPointer<wlr_xcursor_manager>? = null
    private var pointerFocus: CPointer<wlr_surface>? = null
    private var enteredPopup: Popup? = null
    private var lastSx: Double = 0.0
    private var lastSy: Double = 0.0
    private var interaction: Interaction = Interaction.None
    private var lastMinimized: Window? = null

    private val motionListener = SignalListener()
    private val motionAbsoluteListener = SignalListener()
    private val buttonListener = SignalListener()
    private val axisListener = SignalListener()
    private val keyboardListeners = mutableListOf<KeyboardBinding>()

    fun init() {
        cursor = wlr_cursor_create()
        xcursorManager = wlr_xcursor_manager_create(null, 24u)
        wlr_cursor_attach_output_layout(cursor, server.outputLayout)

        val c = cursor ?: return
        xcursorManager?.let { manager ->
            if (wlr_xcursor_manager_load(manager, 1f)) {
                wlr_cursor_set_xcursor(c, manager, "left_ptr")
            }
        }
        motionListener.onSignal = { _, data -> onMotion(data!!) }
        motionListener.connect(wlr_shim_cursor_motion(c)!!)
        motionAbsoluteListener.onSignal = { _, data -> onMotionAbsolute(data!!) }
        motionAbsoluteListener.connect(wlr_shim_cursor_motion_absolute(c)!!)
        buttonListener.onSignal = { _, data -> onButton(data!!) }
        buttonListener.connect(wlr_shim_cursor_button(c)!!)
        axisListener.onSignal = { _, data -> onAxis(data!!) }
        axisListener.connect(wlr_shim_cursor_axis(c)!!)
    }

    fun startMoveFromRequest(window: Window) {
        val c = cursor ?: return
        server.windowManager.focus(window)
        interaction = Interaction.Move(
            window, c.pointed.x.toInt(), c.pointed.y.toInt(), window.x, window.y
        )
    }

    fun setMaximizedFromRequest(window: Window, maximized: Boolean) {
        val toplevel = window.xdgSurface?.let { wlr_shim_xdg_surface_toplevel(it) }
        if (maximized) {
            if (!window.maximized) window.saveGeometry()
            memScoped {
                val bx = alloc<IntVar>()
                val by = alloc<IntVar>()
                val bw = alloc<IntVar>()
                val bh = alloc<IntVar>()
                val ok = wlr_shim_output_layout_box(
                    server.outputLayout, bx.ptr, by.ptr, bw.ptr, bh.ptr
                )
                if (ok == 1u) {
                    window.configureGeometry(bx.value, by.value, bw.value, bh.value)
                }
            }
        } else if (window.maximized) {
            window.restoreGeometry()
        }
        window.maximized = maximized
        if (window.xdgReady) toplevel?.let { wlr_xdg_toplevel_set_maximized(it, maximized) }
        window.notifyGeometry()
    }

    fun setMinimizedFromRequest(window: Window, minimized: Boolean) {
        val toplevel = window.xdgSurface?.let { wlr_shim_xdg_surface_toplevel(it) }
        window.minimized = minimized
        if (window.xdgReady) toplevel?.let { wlr_xdg_toplevel_set_suspended(it, minimized) }
        wlr_scene_node_set_enabled(window.node, !minimized)
        if (minimized) {
            lastMinimized = window
            server.windowManager.clearFocus()
        } else {
            if (lastMinimized === window) lastMinimized = null
        }
    }

    fun restoreLastMinimized() {
        val window = lastMinimized ?: return
        setMinimizedFromRequest(window, false)
        server.windowManager.focus(window)
        lastMinimized = null
    }

    fun onNewInput(data: COpaquePointer) {
        val device = data.reinterpret<wlr_input_device>()
        when (wlr_shim_input_device_type(device)) {
            0u -> {
                val keyboard = wlr_shim_keyboard_from_device(device) ?: return
                val context = xkb_context_new(XKB_CONTEXT_NO_FLAGS) ?: return
                val keymap = xkb_keymap_new_from_names(context, null, XKB_KEYMAP_COMPILE_NO_FLAGS)
                if (keymap != null) {
                    wlr_keyboard_set_keymap(keyboard, keymap)
                    xkb_keymap_unref(keymap)
                }
                xkb_context_unref(context)

                wlr_seat_set_keyboard(server.seat, keyboard)
                wlr_seat_set_capabilities(
                    server.seat,
                    WL_SEAT_CAPABILITY_KEYBOARD or WL_SEAT_CAPABILITY_POINTER
                )

                val keyListener = SignalListener()
                keyListener.onSignal = { _, d -> onKey(d!!) }
                keyListener.connect(wlr_shim_keyboard_key(keyboard)!!)
                val modListener = SignalListener()
                modListener.onSignal = { _, _ -> onModifiers() }
                modListener.connect(wlr_shim_keyboard_modifiers(keyboard)!!)
                keyboardListeners.add(KeyboardBinding(keyboard, keyListener, modListener))
            }

            1u -> {
                wlr_cursor_attach_input_device(cursor, device)
                wlr_seat_set_capabilities(
                    server.seat,
                    WL_SEAT_CAPABILITY_KEYBOARD or WL_SEAT_CAPABILITY_POINTER
                )
            }

            else -> {}
        }
    }

    private fun onMotion(data: COpaquePointer) {
        val device = wlr_shim_motion_device(data)
        wlr_cursor_move(cursor, device, wlr_shim_motion_dx(data), wlr_shim_motion_dy(data))
        afterCursorMove(wlr_shim_motion_time(data))
    }

    private fun onMotionAbsolute(data: COpaquePointer) {
        val device = wlr_shim_motion_absolute_device(data)
        wlr_cursor_warp_absolute(
            cursor, device,
            wlr_shim_motion_absolute_x(data),
            wlr_shim_motion_absolute_y(data)
        )
        afterCursorMove(wlr_shim_motion_absolute_time(data))
    }

    private fun pointerTargetAt(cx: Int, cy: Int): PointerTarget? {
        val layer = server.layerManager.surfaceAt(cx, cy)
        if (layer != null) {
            return PointerTarget(
                layer.surface,
                (cx - layer.x).toDouble(),
                (cy - layer.y).toDouble(),
                null,
                null
            )
        }
        val popup = server.windowManager.popupAt(cx, cy)
        if (popup != null) {
            return PointerTarget(
                popup.surface,
                (cx - popup.x + popup.geomX).toDouble(),
                (cy - popup.y + popup.geomY).toDouble(),
                popup,
                null
            )
        }
        val window = server.windowManager.topmostAt(cx, cy) ?: return null
        return PointerTarget(
            window.surface,
            (cx - window.x + window.geomX).toDouble(),
            (cy - window.y - window.decorOffset + window.geomY).toDouble(),
            null,
            window
        )
    }

    private fun ensurePointerEnter(target: PointerTarget) {
        val popup = target.popup
        if (popup != null) {
            if (enteredPopup !== popup || pointerFocus != target.surface) {
                wlr_seat_pointer_notify_enter(server.seat, target.surface, target.sx, target.sy)
                pointerFocus = target.surface
                enteredPopup = popup
            }
        } else {
            enteredPopup = null
            if (pointerFocus != target.surface) {
                wlr_seat_pointer_notify_enter(server.seat, target.surface, target.sx, target.sy)
                pointerFocus = target.surface
            }
        }
    }

    fun onPopupDestroyed(popup: Popup) {
        if (enteredPopup === popup) enteredPopup = null
        if (pointerFocus == popup.surface) {
            wlr_seat_pointer_clear_focus(server.seat)
            pointerFocus = null
        }
    }

    private fun afterCursorMove(time: UInt) {
        val cx = cursor!!.pointed.x.toInt()
        val cy = cursor!!.pointed.y.toInt()

        when (val it = interaction) {
            is Interaction.Move -> it.window.configureGeometry(
                it.origX + (cx - it.startX), it.origY + (cy - it.startY),
                it.window.width, it.window.height
            )

            is Interaction.Resize -> applyResize(it, cx, cy)
            is Interaction.None -> {
                val target = pointerTargetAt(cx, cy)
                if (target != null) {
                    lastSx = target.sx
                    lastSy = target.sy
                }
                if (target?.popup != null) ensurePointerEnter(target)
                if (wlr_seat_pointer_has_grab(server.seat)) {
                    wlr_seat_pointer_notify_motion(
                        server.seat, time, target?.sx ?: lastSx, target?.sy ?: lastSy
                    )
                    wlr_seat_pointer_notify_frame(server.seat)
                } else if (target != null) {
                    if (target.popup == null) ensurePointerEnter(target)
                    wlr_seat_pointer_notify_motion(server.seat, time, target.sx, target.sy)
                    wlr_seat_pointer_notify_frame(server.seat)
                } else {
                    wlr_seat_pointer_clear_focus(server.seat)
                    pointerFocus = null
                    enteredPopup = null
                }
            }
        }
    }

    private fun applyResize(r: Interaction.Resize, cx: Int, cy: Int) {
        val dx = cx - r.startX
        var nx = r.origX
        var ny = r.origY
        var nw = r.origW
        var nh = r.origH
        when (r.edges) {
            HitResult.RESIZE_E, HitResult.RESIZE_NE, HitResult.RESIZE_SE -> nw = r.origW + dx
            HitResult.RESIZE_W, HitResult.RESIZE_NW, HitResult.RESIZE_SW -> {
                nw = r.origW - dx
                nx = r.origX + dx
            }

            else -> {}
        }
        val dy = cy - r.startY
        when (r.edges) {
            HitResult.RESIZE_S, HitResult.RESIZE_SE, HitResult.RESIZE_SW -> nh = r.origH + dy
            HitResult.RESIZE_N, HitResult.RESIZE_NW, HitResult.RESIZE_NE -> {
                nh = r.origH - dy
                ny = r.origY + dy
            }

            else -> {}
        }
        r.window.configureGeometry(nx, ny, nw.coerceAtLeast(100), nh.coerceAtLeast(100))
        r.window.notifyGeometry()
    }

    private fun onButton(data: COpaquePointer) {
        val button = wlr_shim_button_event_button(data)
        val state = wlr_shim_button_event_state(data)
        val cx = cursor!!.pointed.x.toInt()
        val cy = cursor!!.pointed.y.toInt()
        val target = pointerTargetAt(cx, cy)
        if (target?.popup != null) {
            lastSx = target.sx
            lastSy = target.sy
        }
        val grabbing = wlr_seat_pointer_has_grab(server.seat)
        val newPopupTarget = target?.popup != null && enteredPopup !== target.popup
        if (target?.popup != null) {
            ensurePointerEnter(target)
        }
        if (!grabbing) {
            if (target != null) {
                if (target.popup == null) ensurePointerEnter(target)
            } else {
                wlr_seat_pointer_clear_focus(server.seat)
                pointerFocus = null
                enteredPopup = null
            }
        }
        val time = wlr_shim_button_event_time(data)
        if (newPopupTarget) {
            wlr_seat_pointer_notify_motion(server.seat, time, target.sx, target.sy)
            wlr_seat_pointer_notify_frame(server.seat)
        }
        wlr_seat_pointer_notify_button(server.seat, time, button, state)
        wlr_seat_pointer_notify_frame(server.seat)

        if (state == wlr_button_state.WLR_BUTTON_PRESSED.value) {
            if (grabbing) {
                interaction = Interaction.None
                return
            }
            val window = target?.window
            if (window == null) {
                interaction = Interaction.None
                return
            }
            server.windowManager.focus(window)
            interaction = when (val hit = window.hitTest(cx, cy)) {
                HitResult.MOVE -> Interaction.Move(window, cx, cy, window.x, window.y)
                HitResult.RESIZE_N, HitResult.RESIZE_S, HitResult.RESIZE_E, HitResult.RESIZE_W,
                HitResult.RESIZE_NW, HitResult.RESIZE_NE, HitResult.RESIZE_SW, HitResult.RESIZE_SE ->
                    Interaction.Resize(
                        window, hit, cx, cy, window.x, window.y, window.width, window.height
                    )

                else -> Interaction.None
            }
        } else if (state == wlr_button_state.WLR_BUTTON_RELEASED.value) {
            interaction = Interaction.None
        }
    }

    private fun onAxis(data: COpaquePointer) {
        val c = cursor ?: return
        memScoped {
            val time = alloc<UIntVar>()
            val source = alloc<UIntVar>()
            val orientation = alloc<UIntVar>()
            val relative = alloc<UIntVar>()
            val delta = alloc<DoubleVar>()
            val discrete = alloc<IntVar>()
            wlr_shim_axis_event(
                data, time.ptr, source.ptr, orientation.ptr,
                relative.ptr, delta.ptr, discrete.ptr
            )

            val target = pointerTargetAt(c.pointed.x.toInt(), c.pointed.y.toInt())
            if (target != null && !wlr_seat_pointer_has_grab(server.seat)) {
                ensurePointerEnter(target)
            }
            wlr_shim_seat_notify_axis(
                server.seat, time.value, orientation.value, delta.value,
                discrete.value, source.value, relative.value
            )
            wlr_seat_pointer_notify_frame(server.seat)
        }
    }

    private fun onKey(data: COpaquePointer) {
        val keycode = wlr_shim_key_event_keycode(data)
        val state = wlr_shim_key_event_state(data)
        val time = wlr_shim_key_event_time(data)
        val keyboard = wlr_seat_get_keyboard(server.seat) ?: return
        wlr_seat_keyboard_notify_key(server.seat, time, keycode, state)

        val mods = wlr_keyboard_get_modifiers(keyboard)
        val superMod = 1u shl 6
        if (state == WL_KEYBOARD_KEY_STATE_PRESSED && (mods and superMod) != 0u) {
            when (keycode) {
                17u -> server.windowManager.focused?.close()
                50u -> restoreLastMinimized()
            }
        }
    }

    private fun onModifiers() {
        val kb = wlr_seat_get_keyboard(server.seat) ?: return
        val mods = wlr_keyboard_get_modifiers(kb)
        wlr_shim_seat_notify_modifiers(server.seat, mods, 0u, 0u, 0u)
    }

    fun dispose() {
        motionListener.disconnect()
        motionAbsoluteListener.disconnect()
        buttonListener.disconnect()
        axisListener.disconnect()
        keyboardListeners.forEach {
            it.keyListener.disconnect()
            it.modifierListener.disconnect()
        }
        keyboardListeners.clear()
        cursor?.let { wlr_cursor_destroy(it) }
        xcursorManager?.let { wlr_xcursor_manager_destroy(it) }
        cursor = null
        xcursorManager = null
    }
}

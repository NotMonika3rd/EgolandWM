@file:OptIn(ExperimentalForeignApi::class)

package reverie.egoland.protocol

import kotlinx.cinterop.*
import reverie.egoland.Server
import reverie.egoland.SignalListener
import reverie.egoland.Window
import wlr.*

private class DragState(
    val drag: CPointer<wlr_drag>,
    val focusListener: SignalListener,
    val motionListener: SignalListener,
    val destroyListener: SignalListener,
    var iconTree: CPointer<wlr_scene_tree>? = null
)

private const val TARGET_BRIGHTNESS_ALPHA = 0.12f

class DataDeviceManager(val server: Server) {
    private val requestSetSelectionListener = SignalListener()
    private val requestStartDragListener = SignalListener()
    private val dragStates = mutableListOf<DragState>()
    private var dragIconRoot: CPointer<wlr_scene_tree>? = null
    private var dropTargetWindow: Window? = null

    var dragging: Boolean = false
        private set

    fun init() {
        setupVisualLayer()

        val seat = server.seat ?: return
        requestSetSelectionListener.onSignal = { _, data ->
            onRequestSetSelection(data!!)
        }
        requestSetSelectionListener.connect(wlr_shim_seat_request_set_selection(seat)!!)

        requestStartDragListener.onSignal = { _, data -> onRequestStartDrag(data!!) }
        requestStartDragListener.connect(wlr_shim_seat_request_start_drag(seat)!!)
    }

    private fun setupVisualLayer() {
        if (dragIconRoot != null) return

        val scene = server.scene ?: return
        val root = wlr_shim_scene_root_tree(scene) ?: return
        val iconRoot = wlr_scene_tree_create(root) ?: return
        dragIconRoot = iconRoot

        hideDragVisuals()
    }

    private fun raiseDragVisualLayer() {
        dragIconRoot?.let { wlr_scene_node_raise_to_top(it.pointed.node.ptr) }
    }

    private fun onRequestSetSelection(data: COpaquePointer) {
        val event = data.reinterpret<wlr_seat_request_set_selection_event>().pointed
        wlr_seat_set_selection(server.seat, event.source, event.serial)
    }

    private fun onRequestStartDrag(data: COpaquePointer) {
        val event = data.reinterpret<wlr_seat_request_start_drag_event>().pointed
        val drag = event.drag ?: return
        val seat = server.seat ?: return

        if (!wlr_seat_validate_pointer_grab_serial(seat, event.origin, event.serial)) {
            wlr_shim_drag_source(drag)?.let { wlr_data_source_destroy(it) }
            return
        }

        setupDrag(drag)
        wlr_seat_start_pointer_drag(seat, drag, event.serial)
        server.inputManager.onDragStarted()
    }

    private fun setupDrag(drag: CPointer<wlr_drag>) {
        dragging = true
        raiseDragVisualLayer()

        val focusListener = SignalListener()
        focusListener.onSignal = { _, _ ->
            updateDragIconPosition(drag)
            server.inputManager.cursorPosition()?.let { (x, y) -> updateDragVisuals(x, y) }
        }
        focusListener.connect(wlr_shim_drag_focus(drag)!!)

        val motionListener = SignalListener()
        motionListener.onSignal = { _, _ ->
            updateDragIconPosition(drag)
            server.inputManager.cursorPosition()?.let { (x, y) -> updateDragVisuals(x, y) }
        }
        motionListener.connect(wlr_shim_drag_motion(drag)!!)

        val selfRef = arrayOfNulls<SignalListener>(1)
        val destroyListener = SignalListener()
        selfRef[0] = destroyListener
        destroyListener.onSignal = { _, _ ->
            val state = dragStates.firstOrNull { it.drag === drag }
            state?.let {
                it.focusListener.disconnect()
                it.motionListener.disconnect()
                dragStates.remove(it)
            }
            wlr_shim_drag_source(drag)?.let { wlr_data_source_dnd_finish(it) }
            hideDragVisuals()
            dropTargetWindow = null
            dragging = false
            server.inputManager.onDragEnded()
            selfRef[0]?.disconnect()
        }
        destroyListener.connect(wlr_shim_drag_destroy(drag)!!)

        val state = DragState(drag, focusListener, motionListener, destroyListener)
        dragStates.add(state)

        val icon = wlr_shim_drag_icon(drag)
        if (icon != null && dragIconRoot != null) {
            state.iconTree = wlr_scene_drag_icon_create(dragIconRoot, icon)
            state.iconTree?.let { wlr_scene_node_raise_to_top(it.pointed.node.ptr) }
            updateDragIconPosition(drag)
        }
        server.inputManager.cursorPosition()?.let { (x, y) -> updateDragVisuals(x, y) }
    }

    private fun updateDragIconPosition(drag: CPointer<wlr_drag>) {
        val tree = dragStates.firstOrNull { it.drag === drag }?.iconTree ?: return
        val position = server.inputManager.cursorPosition() ?: return
        wlr_scene_node_set_position(tree.pointed.node.ptr, position.first, position.second)
    }

    private fun brightnessRect(window: Window): CPointer<wlr_scene_rect> {
        window.brightnessNode?.let { return it }

        val a = TARGET_BRIGHTNESS_ALPHA
        val rect = wlr_shim_scene_rect_create(
            window.contentTree, 1, 1, a, a, a, a
        )!!
        wlr_scene_node_set_enabled(rect.pointed.node.ptr, false)
        window.brightnessNode = rect
        return rect
    }

    private fun showDropTarget(window: Window) {
        val rect = brightnessRect(window)
        wlr_scene_rect_set_size(rect, window.width, window.height)
        wlr_scene_node_set_position(rect.pointed.node.ptr, 0, 0)
        wlr_scene_node_set_enabled(rect.pointed.node.ptr, true)
        wlr_scene_node_raise_to_top(rect.pointed.node.ptr)
    }

    fun updateDragVisuals(cx: Int, cy: Int) {
        if (!dragging) return

        raiseDragVisualLayer()

        val window = server.windowManager.topmostAt(cx, cy)
        if (dropTargetWindow !== window) {
            hideDropTarget()
            dropTargetWindow = window
        }
        if (window != null) showDropTarget(window) else hideDropTarget()
    }

    private fun hideDropTarget() {
        dropTargetWindow?.brightnessNode?.let {
            wlr_scene_node_set_enabled(it.pointed.node.ptr, false)
        }
    }

    fun onWindowDestroyed(window: Window) {
        if (dropTargetWindow === window) dropTargetWindow = null
    }

    private fun hideDragVisuals() {
        hideDropTarget()
    }

    fun dispose() {
        requestSetSelectionListener.disconnect()
        requestStartDragListener.disconnect()
        dragStates.toList().forEach { state ->
            state.focusListener.disconnect()
            state.motionListener.disconnect()
            state.destroyListener.disconnect()
        }
        dragStates.clear()
        hideDragVisuals()
        dropTargetWindow = null
        dragging = false
    }
}

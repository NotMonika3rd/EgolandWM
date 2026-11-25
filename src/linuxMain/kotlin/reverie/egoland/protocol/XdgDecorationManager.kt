@file:OptIn(ExperimentalForeignApi::class)

package reverie.egoland.protocol

import cnames.structs.wl_event_loop
import cnames.structs.wl_event_source
import kotlinx.cinterop.*
import reverie.egoland.DecorationMode
import reverie.egoland.Server
import reverie.egoland.SignalListener
import reverie.egoland.Window
import wlr.*

private class DecorationIdleState(
    val decoration: CPointer<wlr_xdg_toplevel_decoration_v1>,
    val mode: UInt,
    var active: Boolean = true
)

private class DecorationConfigureIdle(
    private val loop: CPointer<wl_event_loop>?,
    private val decoration: CPointer<wlr_xdg_toplevel_decoration_v1>,
    private val mode: UInt
) {
    private var state: DecorationIdleState? = null
    private var ref: StableRef<DecorationIdleState>? = null
    private var source: CPointer<wl_event_source>? = null

    companion object {
        private val callback = staticCFunction<COpaquePointer?, Unit> { data ->
            val state = data!!.asStableRef<DecorationIdleState>().get()
            if (state.active) {
                state.active = false
                wlr_shim_xdg_toplevel_decoration_set_mode(state.decoration, state.mode)
            }
        }
    }

    fun schedule() {
        if (source != null) return
        val newState = DecorationIdleState(decoration, mode)
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

private class DecorationBinding(
    val decoration: CPointer<wlr_xdg_toplevel_decoration_v1>,
    val requestListener: SignalListener,
    val destroyListener: SignalListener,
    var window: Window?,
    var idle: DecorationConfigureIdle? = null
)

class XdgDecorationManager(val server: Server) {
    private val newDecorationListener = SignalListener()
    private val decorations = mutableListOf<DecorationBinding>()

    fun init() {
        val manager = server.xdgDecorationManager ?: return
        newDecorationListener.onSignal = { _, data -> onNewDecoration(data!!) }
        newDecorationListener.connect(
            wlr_shim_xdg_decoration_manager_new_toplevel(manager)!!
        )
    }

    fun applyConfiguredMode(window: Window) {
        if (server.config.decoration == DecorationMode.SERVER) {
            window.setClientDecorated(false)
        }
    }

    fun attach(window: Window) {
        val toplevel = window.xdgSurface?.let { wlr_shim_xdg_surface_toplevel(it) } ?: return
        decorations.firstOrNull {
            it.window == null && wlr_shim_xdg_toplevel_decoration_toplevel(it.decoration) == toplevel
        }?.let {
            it.window = window
            applyDecorationMode(it)
        }
    }

    private fun onNewDecoration(data: COpaquePointer) {
        val decoration = data.reinterpret<wlr_xdg_toplevel_decoration_v1>()
        val toplevel = wlr_shim_xdg_toplevel_decoration_toplevel(decoration) ?: return

        val requestListener = SignalListener()
        val destroyListener = SignalListener()
        val binding = DecorationBinding(decoration, requestListener, destroyListener, null)

        requestListener.onSignal = { _, _ -> applyDecorationMode(binding) }
        destroyListener.onSignal = { _, _ ->
            binding.idle?.dispose()
            requestListener.disconnect()
            destroyListener.disconnect()
            decorations.remove(binding)
        }
        destroyListener.connect(wlr_shim_xdg_toplevel_decoration_destroy(decoration)!!)
        requestListener.connect(wlr_shim_xdg_toplevel_decoration_request_mode(decoration)!!)

        binding.window = server.windowManager.windows.firstOrNull {
            it.xdgSurface != null &&
                wlr_shim_xdg_surface_toplevel(it.xdgSurface!!) == toplevel
        }
        decorations.add(binding)
        applyDecorationMode(binding)
    }

    private fun applyDecorationMode(binding: DecorationBinding) {
        val requested = wlr_shim_xdg_toplevel_decoration_requested_mode(binding.decoration)
        val mode = when (server.config.decoration) {
            DecorationMode.CLIENT -> 1u
            DecorationMode.SERVER -> 2u
            DecorationMode.AUTO -> if (requested == 0u) 1u else requested
        }
        binding.idle?.dispose()
        val idle = DecorationConfigureIdle(server.eventLoop, binding.decoration, mode)
        binding.idle = idle
        idle.schedule()
        binding.window?.setClientDecorated(mode != 2u)
    }

    fun dispose() {
        newDecorationListener.disconnect()
        decorations.forEach {
            it.idle?.dispose()
            it.requestListener.disconnect()
            it.destroyListener.disconnect()
        }
        decorations.clear()
    }
}

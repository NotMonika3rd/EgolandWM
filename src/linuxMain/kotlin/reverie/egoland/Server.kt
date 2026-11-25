@file:OptIn(ExperimentalForeignApi::class)

package reverie.egoland

import cnames.structs.wl_display
import cnames.structs.wl_event_loop
import kotlinx.cinterop.*
import platform.posix.system
import wlr.*

typealias SignalCallback = (CPointer<wl_listener>?, COpaquePointer?) -> Unit

class SignalListener {
    private var handle: CPointer<wlr_shim_listener>? = null
    private var ref: StableRef<SignalCallback>? = null

    companion object {
        private val dispatcher = staticCFunction<CPointer<wl_listener>?, COpaquePointer?, Unit> { listener, data ->
            val callback = wlr_shim_listener_data(listener)!!.asStableRef<SignalCallback>().get()
            callback(listener, data)
        }
    }

    fun connect(signal: CPointer<wl_signal>) {
        disconnect()
        val callback: SignalCallback = { listener, data -> this.dispatch(listener, data) }
        ref = StableRef.create(callback)
        val h = nativeHeap.alloc<wlr_shim_listener>()
        h.user_data = ref!!.asCPointer()
        h.listener.notify = dispatcher
        wlr_shim_signal_add(signal, h.ptr)
        handle = h.ptr
    }

    var onSignal: SignalCallback? = null
    private fun dispatch(listener: CPointer<wl_listener>?, data: COpaquePointer?) {
        onSignal?.invoke(listener, data)
    }

    fun disconnect() {
        handle?.let { wlr_shim_listener_remove(it) }
        handle?.let { nativeHeap.free(it) }
        handle = null
        ref?.dispose()
        ref = null
    }
}
class Server {
    val config: Config = Config.load()

    var display: CPointer<wl_display>? = null
        private set
    var eventLoop: CPointer<wl_event_loop>? = null
        private set
    var backend: CPointer<wlr_backend>? = null
        private set
    var renderer: CPointer<wlr_renderer>? = null
        private set
    var allocator: CPointer<wlr_allocator>? = null
        private set
    var scene: CPointer<wlr_scene>? = null
        private set
    var outputLayout: CPointer<wlr_output_layout>? = null
        private set
    var compositor: CPointer<wlr_compositor>? = null
        private set
    var xdgShell: CPointer<wlr_xdg_shell>? = null
        private set
    var xdgDecorationManager: CPointer<wlr_xdg_decoration_manager_v1>? = null
        private set
    var layerShell: CPointer<wlr_layer_shell_v1>? = null
        private set
    var seat: CPointer<wlr_seat>? = null
        private set

    val outputManager = OutputManager(this)
    val windowManager = WindowManager(this)
    val layerManager = LayerManager(this)
    val inputManager = InputManager(this)
    val xwaylandManager = XWaylandManager(this)

    private val backendDestroyListener = SignalListener()
    private val newOutputListener = SignalListener()
    private val newInputListener = SignalListener()

    var socket: String? = null
        private set

    fun start(): Boolean {
        display = wl_display_create() ?: run {
            println("egoland: failed to create wl_display")
            return false
        }
        eventLoop = wl_display_get_event_loop(display)

        backend = wlr_backend_autocreate(eventLoop, null) ?: run {
            println("egoland: failed to create backend (check DRM/WAYLAND_DISPLAY)")
            return false
        }

        renderer = wlr_renderer_autocreate(backend) ?: run {
            println("egoland: failed to create renderer")
            return false
        }
        if (!wlr_renderer_init_wl_display(renderer, display)) {
            println("egoland: failed to init renderer with display")
            return false
        }

        allocator = wlr_allocator_autocreate(backend, renderer) ?: run {
            println("egoland: failed to create allocator")
            return false
        }

        scene = wlr_scene_create() ?: run {
            println("egoland: failed to create scene")
            return false
        }
        outputLayout = wlr_output_layout_create(display) ?: run {
            println("egoland: failed to create output layout")
            return false
        }
        wlr_scene_attach_output_layout(scene, outputLayout)

        compositor = wlr_compositor_create(display, 6u, renderer) ?: run {
            println("egoland: failed to create compositor")
            return false
        }

        xdgShell = wlr_xdg_shell_create(display, 6u) ?: run {
            println("egoland: failed to create xdg shell")
            return false
        }
        xdgDecorationManager = wlr_xdg_decoration_manager_v1_create(display)
        if (xdgDecorationManager == null) {
            println("egoland: warning: failed to create xdg decoration manager")
        }
        layerShell = wlr_layer_shell_v1_create(display, 4u) ?: run {
            println("egoland: failed to create layer shell")
            return false
        }
        wlr_data_device_manager_create(display)

        seat = wlr_seat_create(display, "seat0") ?: run {
            println("egoland: failed to create seat")
            return false
        }

        newOutputListener.onSignal = { _, data -> outputManager.onNewOutput(data!!) }
        newOutputListener.connect(wlr_shim_backend_new_output(backend)!!)
        newInputListener.onSignal = { _, data -> inputManager.onNewInput(data!!) }
        newInputListener.connect(wlr_shim_backend_new_input(backend)!!)
        backendDestroyListener.onSignal = { _, _ -> wl_display_terminate(display) }
        backendDestroyListener.connect(wlr_shim_backend_destroy(backend)!!)

        windowManager.init()
        layerManager.init()
        inputManager.init()
        if (config.xwayland) {
            xwaylandManager.init()
        } else {
            println("egoland: XWayland disabled by config")
        }

        socket = wl_display_add_socket_auto(display)?.toKString()
        if (socket == null) {
            println("egoland: failed to add wayland socket")
            return false
        }
        setenv("WAYLAND_DISPLAY", socket, 1)
        println("egoland: running on WAYLAND_DISPLAY=$socket")

        if (!wlr_backend_start(backend)) {
            println("egoland: failed to start backend")
            return false
        }
        runAutostart()
        return true
    }

    private fun runAutostart() {
        config.autostart.forEach { command ->
            if (command.isBlank()) return@forEach
            println("egoland: autostart: $command")
            system("$command >/dev/null 2>&1 &")
        }
    }

    fun run() {
        wl_display_run(display)
    }

    fun cleanup() {
        if (display == null) return
        xwaylandManager.dispose()
        inputManager.dispose()
        layerManager.dispose()
        windowManager.dispose()
        outputManager.dispose()
        newInputListener.disconnect()
        newOutputListener.disconnect()
        backendDestroyListener.disconnect()

        wl_display_destroy_clients(display)
        backend?.let { wlr_backend_destroy(it) }
        wl_display_destroy(display)
        display = null
        println("egoland: shutdown")
    }
}

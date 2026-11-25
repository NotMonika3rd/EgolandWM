@file:OptIn(ExperimentalForeignApi::class)

package reverie.egoland.protocol

import kotlinx.cinterop.*
import platform.posix.CLOCK_MONOTONIC
import reverie.egoland.Server
import reverie.egoland.SignalListener
import wlr.*

class Output(
    val server: Server,
    val wlrOutput: CPointer<wlr_output>
) {
    val sceneOutput: CPointer<wlr_scene_output>?
    private val frameListener = SignalListener()
    private val destroyListener = SignalListener()
    private var disposed = false

    init {
        configure()
        sceneOutput = wlr_scene_output_create(server.scene, wlrOutput)
        if (sceneOutput != null) {
            wlr_output_layout_add_auto(server.outputLayout, wlrOutput)
            wlr_scene_output_commit(sceneOutput, null)
        }

        frameListener.onSignal = { _, _ ->
            sceneOutput?.let { so ->
                wlr_scene_output_commit(so, null)
                memScoped {
                    val ts = alloc<timespec>()
                    clock_gettime(CLOCK_MONOTONIC, ts.ptr)
                    wlr_scene_output_send_frame_done(so, ts.ptr)
                }
            }
        }
        frameListener.connect(wlr_shim_output_frame(wlrOutput)!!)

        destroyListener.onSignal = { _, _ ->
            if (!disposed) {
                disposed = true
                println("egoland: output destroyed")
                frameListener.disconnect()
                destroyListener.disconnect()
                sceneOutput?.let { wlr_scene_output_destroy(it) }
                wlr_output_layout_remove(server.outputLayout, wlrOutput)
                server.outputManager.onOutputDestroyed(this@Output)
            }
        }
        destroyListener.connect(wlr_shim_output_destroy(wlrOutput)!!)
    }

    private fun configure() {
        if (!wlr_output_init_render(wlrOutput, server.allocator, server.renderer)) {
            println("egoland: warning: failed to init output renderer")
        }
        memScoped {
            val state = alloc<wlr_output_state>()
            wlr_output_state_init(state.ptr)
            val mode = wlr_output_preferred_mode(wlrOutput)
            if (mode != null) {
                wlr_output_state_set_mode(state.ptr, mode)
            } else {
                wlr_output_state_set_custom_mode(state.ptr, 1280, 720, 0)
            }
            wlr_output_state_set_enabled(state.ptr, true)
            if (!wlr_output_commit_state(wlrOutput, state.ptr)) {
                println("egoland: warning: failed to configure output")
            }
            wlr_output_state_finish(state.ptr)
        }
        wlr_shim_output_ensure_refresh(wlrOutput, 60_000)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        frameListener.disconnect()
        destroyListener.disconnect()
        sceneOutput?.let { wlr_scene_output_destroy(it) }
        wlr_output_layout_remove(server.outputLayout, wlrOutput)
    }
}

class OutputManager(val server: Server) {
    private val outputs = mutableListOf<Output>()
    private var terminating = false

    fun onNewOutput(data: COpaquePointer) {
        val wlrOutput = data.reinterpret<wlr_output>()
        println("egoland: new output ${wlr_shim_output_name(wlrOutput)?.toKString() ?: "unknown"}")
        outputs.add(Output(server, wlrOutput))
    }

    fun firstOutput(): CPointer<wlr_output>? = outputs.firstOrNull()?.wlrOutput

    fun onOutputDestroyed(output: Output) {
        outputs.remove(output)
        if (outputs.isEmpty() && !terminating) {
            terminating = true
            println("egoland: all outputs destroyed, terminating")
            server.display?.let { wl_display_terminate(it) }
        }
    }

    fun dispose() {
        outputs.toList().forEach { it.dispose() }
        outputs.clear()
    }
}

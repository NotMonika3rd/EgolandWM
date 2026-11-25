@file:OptIn(ExperimentalForeignApi::class)

package reverie.egoland

import kotlinx.cinterop.*
import wlr.*

internal fun sceneNodeAbsolutePosition(node: CPointer<wlr_scene_node>): Pair<Int, Int> {
    var current: CPointer<wlr_scene_node>? = node
    var x = 0
    var y = 0
    while (current != null) {
        memScoped {
            val nx = alloc<IntVar>()
            val ny = alloc<IntVar>()
            wlr_shim_scene_node_position(current, nx.ptr, ny.ptr)
            x += nx.value
            y += ny.value
        }
        current = wlr_shim_scene_node_parent(current)
    }
    return x to y
}

class LayerSurface(
    val layerSurface: CPointer<wlr_layer_surface_v1>,
    val surface: CPointer<wlr_surface>,
    val sceneSurface: CPointer<wlr_scene_layer_surface_v1>
) {
    private val node = sceneSurface.pointed.tree!!.pointed.node.ptr
    var x: Int = 0
        private set
    var y: Int = 0
        private set
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    fun updateGeometry() {
        val position = sceneNodeAbsolutePosition(node)
        x = position.first
        y = position.second
        memScoped {
            val w = alloc<UIntVar>()
            val h = alloc<UIntVar>()
            wlr_shim_layer_surface_size(layerSurface, w.ptr, h.ptr)
            width = w.value.toInt()
            height = h.value.toInt()
        }
    }

    fun containsPoint(px: Int, py: Int): Boolean =
        px >= x && px < x + width && py >= y && py < y + height
}

class LayerManager(val server: Server) {
    private val newSurfaceListener = SignalListener()
    private val layerTrees = arrayOfNulls<CPointer<wlr_scene_tree>>(4)
    private val surfaces = mutableListOf<LayerSurface>()

    fun init() {
        val root = wlr_shim_scene_root_tree(server.scene) ?: return
        for (i in layerTrees.indices) {
            layerTrees[i] = wlr_scene_tree_create(root)
        }
        val shell = server.layerShell ?: return
        newSurfaceListener.onSignal = { _, data -> onNewSurface(data!!) }
        newSurfaceListener.connect(wlr_shim_layer_shell_new_surface(shell)!!)
    }

    fun surfaceAt(px: Int, py: Int): LayerSurface? = surfaces.asReversed().firstOrNull {
        it.containsPoint(px, py)
    }

    private fun onNewSurface(data: COpaquePointer) {
        val layerSurface = data.reinterpret<wlr_layer_surface_v1>()
        val surface = layerSurface.pointed.surface ?: return
        if (layerSurface.pointed.output == null) {
            val output = server.outputManager.firstOutput() ?: return
            wlr_shim_layer_surface_set_output(layerSurface, output)
        }

        val layer = wlr_shim_layer_surface_layer(layerSurface).toInt().coerceIn(0, 3)
        val parent = layerTrees[layer] ?: return
        val sceneSurface = wlr_scene_layer_surface_v1_create(parent, layerSurface) ?: return
        val item = LayerSurface(layerSurface, surface, sceneSurface)
        surfaces.add(item)

        val configureListener = SignalListener()
        configureListener.onSignal = { _, _ -> configure(item) }
        configureListener.connect(wlr_shim_surface_commit(surface)!!)

        val mapListener = SignalListener()
        mapListener.onSignal = { _, _ ->
            wlr_scene_node_set_enabled(sceneSurface.pointed.tree!!.pointed.node.ptr, true)
        }
        mapListener.connect(wlr_shim_surface_map(surface)!!)

        val unmapListener = SignalListener()
        unmapListener.onSignal = { _, _ ->
            wlr_scene_node_set_enabled(sceneSurface.pointed.tree!!.pointed.node.ptr, false)
        }
        unmapListener.connect(wlr_shim_surface_unmap(surface)!!)

        val destroyListener = SignalListener()
        destroyListener.onSignal = { _, _ ->
            configureListener.disconnect()
            mapListener.disconnect()
            unmapListener.disconnect()
            destroyListener.disconnect()
            surfaces.remove(item)
        }
        destroyListener.connect(wlr_shim_layer_surface_destroy(layerSurface)!!)

        configure(item)
        println("egoland: new layer surface on layer $layer")
    }

    private fun configure(item: LayerSurface) {
        if (wlr_shim_layer_surface_initialized(item.layerSurface) == 0u) return
        memScoped {
            val px = alloc<IntVar>()
            val py = alloc<IntVar>()
            val pw = alloc<IntVar>()
            val ph = alloc<IntVar>()
            if (wlr_shim_output_layout_box(
                    server.outputLayout, px.ptr, py.ptr, pw.ptr, ph.ptr
                ) != 1u) return@memScoped
            val full = alloc<wlr_box>()
            val usable = alloc<wlr_box>()
            full.x = px.value
            full.y = py.value
            full.width = pw.value
            full.height = ph.value
            usable.x = full.x
            usable.y = full.y
            usable.width = full.width
            usable.height = full.height
            wlr_shim_scene_layer_surface_configure(item.sceneSurface, full.ptr, usable.ptr)
        }
        item.updateGeometry()
    }

    fun dispose() {
        newSurfaceListener.disconnect()
        surfaces.forEach {
            wlr_scene_node_destroy(it.sceneSurface.pointed.tree!!.pointed.node.ptr)
        }
        surfaces.clear()
    }
}

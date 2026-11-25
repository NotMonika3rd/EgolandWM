@file:OptIn(ExperimentalForeignApi::class)

package reverie.egoland.protocol

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

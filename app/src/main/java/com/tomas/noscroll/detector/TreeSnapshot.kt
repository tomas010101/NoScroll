package com.tomas.noscroll.detector

/** Datos inmutables: los detectores no retienen objetos del framework ni Context. */
data class NodeSnapshot(
    val parent: Int,
    val depth: Int,
    val packageName: String,
    val className: String,
    val viewId: String,
    val text: String,
    val description: String,
    val visible: Boolean,
    val actionable: Boolean,
    val left: Int, val top: Int, val right: Int, val bottom: Int,
    val selected: Boolean = false,
) {
    val width get() = (right - left).coerceAtLeast(0)
    val height get() = (bottom - top).coerceAtLeast(0)
    fun visibleWidth(window: NodeSnapshot) = (minOf(right, window.right) - maxOf(left, window.left)).coerceAtLeast(0)
    fun visibleHeight(window: NodeSnapshot) = (minOf(bottom, window.bottom) - maxOf(top, window.top)).coerceAtLeast(0)
}

data class TreeSnapshot(val nodes: List<NodeSnapshot>, val complete: Boolean) {
    val packageName get() = nodes.firstOrNull()?.packageName.orEmpty()

    fun visibleSubtree(index: Int): List<NodeSnapshot> {
        val anchor = nodes[index]
        return nodes.drop(index).takeWhileIndexed { offset, node ->
            offset == 0 || node.depth > anchor.depth
        }.filter { it.visible && it.packageName == packageName && it.visibleWidth(nodes.first()) > 0 && it.visibleHeight(nodes.first()) > 0 &&
            it.visibleWidth(anchor) > 0 && it.visibleHeight(anchor) > 0 }
    }
}

private fun <T> List<T>.takeWhileIndexed(predicate: (Int, T) -> Boolean): List<T> {
    val end = indices.firstOrNull { !predicate(it, this[it]) } ?: size
    return subList(0, end)
}

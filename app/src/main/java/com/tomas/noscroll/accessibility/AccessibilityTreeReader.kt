package com.tomas.noscroll.accessibility

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.tomas.noscroll.detector.NodeSnapshot
import com.tomas.noscroll.detector.TreeSnapshot

object AccessibilityTreeReader {
    const val MAX_NODES = 450
    const val MAX_DEPTH = 35
    private const val MAX_TEXT = 300

    /** El llamador conserva la propiedad de root; cada hijo obtenido se libera en finally.
     * No guardamos AccessibilityNodeInfo. Límites duros incluso ante árboles cíclicos.
     * Un árbol incompleto nunca autoriza BACK: priorizamos evitar falsos positivos. */
    fun read(root: AccessibilityNodeInfo): TreeSnapshot {
        val nodes = mutableListOf<NodeSnapshot>()
        var complete = true
        fun visit(node: AccessibilityNodeInfo, parent: Int, depth: Int, clickableParent: Boolean) {
            if (nodes.size >= MAX_NODES || depth > MAX_DEPTH) { complete = false; return }
            try {
                val bounds = Rect().also(node::getBoundsInScreen)
                val index = nodes.size
                val actionable = node.isClickable || node.isLongClickable || clickableParent
                nodes += NodeSnapshot(parent, depth, node.packageName?.toString().orEmpty(),
                    node.className?.toString().orEmpty(), node.viewIdResourceName.orEmpty(),
                    node.text?.take(MAX_TEXT)?.toString().orEmpty(),
                    node.contentDescription?.take(MAX_TEXT)?.toString().orEmpty(),
                    node.isVisibleToUser, actionable,
                    bounds.left, bounds.top, bounds.right, bounds.bottom)
                val childCount = node.childCount
                for (i in 0 until childCount) {
                    if (nodes.size >= MAX_NODES || depth >= MAX_DEPTH) { complete = false; break }
                    val child = node.getChild(i)
                    if (child == null) { complete = false; continue }
                    try { visit(child, index, depth + 1, actionable) }
                    finally { release(child) }
                }
            } catch (_: RuntimeException) {
                // La otra aplicación puede reemplazar la ventana mientras se lee.
                complete = false
            }
        }
        visit(root, -1, 0, false)
        return TreeSnapshot(nodes.toList(), complete)
    }

    @Suppress("DEPRECATION")
    fun release(node: AccessibilityNodeInfo) {
        // Desde API 33 se eliminó el pool; recycle es un no-op.
        if (Build.VERSION.SDK_INT < 33) node.recycle()
    }
}

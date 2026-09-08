package com.tomas.noscroll.detector

/** IDs y selección observados en Explorar de Instagram 445.0.0.45.83.
 * La lupa presente en la navegación no basta: debe estar seleccionada y el pager
 * principal debe contener el botón de búsqueda visible. El árbol del servicio
 * omite explore_action_bar, aunque UIAutomator sí lo muestra. */
class InstagramExploreDetector : ContentDetector {
    override val packageName = "com.instagram.android"

    override fun detect(tree: TreeSnapshot): DetectionResult {
        fun result(signals: Map<String, Int>, reason: String) = DetectionResult(
            javaClass.simpleName, signals.values.sum(), 9, signals,
            signals.values.sum() >= 9, reason,
        )
        if (tree.packageName != packageName) return result(emptyMap(), "WRONG_PACKAGE")
        if (!tree.complete) return result(emptyMap(), "INCOMPLETE_TREE")
        val root = tree.nodes.firstOrNull() ?: return result(emptyMap(), "EMPTY_TREE")
        fun visible(node: NodeSnapshot) = node.visible && node.packageName == packageName &&
            node.visibleWidth(root) > 0 && node.visibleHeight(root) > 0
        fun id(name: String) = "$packageName:id/$name"
        val selectedTab = tree.nodes.any {
            visible(it) && it.viewId == id("search_tab") && it.selected
        }
        if (!selectedTab) return result(emptyMap(), "EXPLORE_TAB_NOT_SELECTED")
        val signals = linkedMapOf("EXPLORE_TAB_SELECTED" to 4)
        for ((index, node) in tree.nodes.withIndex()) {
            if (!visible(node) || node.viewId != id("swipeable_tab_view_pager")) continue
            signals["MAIN_TAB_PAGER"] = 2
            if (tree.visibleSubtree(index).any {
                    it.viewId == id("action_bar_search_edit_text") && it.actionable
                }) {
                signals["EXPLORE_SEARCH_CONTROL"] = 3
                return result(signals, "EXPLORE_TAB_AND_SEARCH_BAR")
            }
        }
        return result(signals, "INSUFFICIENT_EXPLORE_SIGNALS")
    }
}

package com.tomas.noscroll.detector

import org.junit.Assert.*
import org.junit.Test

class InstagramExploreDetectorTest {
    private val detector = InstagramExploreDetector()
    private val pkg = detector.packageName

    // Estructura relevante del árbol capturado en Explorar, sin contenido personal.
    private fun node(id: String, parent: Int, depth: Int, top: Int, bottom: Int,
                     selected: Boolean = false, actionable: Boolean = false) = NodeSnapshot(
        parent, depth, pkg, "android.widget.FrameLayout", "$pkg:id/$id", "", "",
        true, actionable, 0, top, 1080, bottom, selected,
    )
    private fun explore() = TreeSnapshot(listOf(
        node("root", -1, 0, 0, 2400),
        node("swipeable_tab_view_pager", 0, 1, 110, 2220),
        node("action_bar_search_edit_text", 1, 2, 124, 247, actionable = true),
        node("search_tab", 0, 1, 2220, 2355, selected = true, actionable = true),
    ), true)

    @Test fun selectedExploreWithSearchBarBlocks() {
        val result = detector.detect(explore())
        assertTrue(result.blocked)
        assertEquals(9, result.score)
    }
    @Test fun navigationTabInFeedDoesNotBlock() {
        val tree = explore()
        assertFalse(detector.detect(tree.copy(nodes = listOf(tree.nodes[0],
            tree.nodes[3].copy(selected = false)))).blocked)
    }
    @Test fun selectedTabWithoutExploreBarDoesNotBlockProfileOrPost() {
        val tree = explore()
        assertFalse(detector.detect(tree.copy(nodes = listOf(tree.nodes[0], tree.nodes[3]))).blocked)
    }
    @Test fun retainedExploreBehindAnotherTabDoesNotBlock() {
        val tree = explore()
        assertFalse(detector.detect(tree.copy(nodes = tree.nodes.map { it.copy(selected = false) })).blocked)
    }
    @Test fun invisibleOrOffscreenExploreDoesNotBlock() {
        val tree = explore()
        for (bar in listOf(tree.nodes[1].copy(visible = false), tree.nodes[1].copy(left = 1080, right = 2160))) {
            assertFalse(detector.detect(tree.copy(nodes = tree.nodes.toMutableList().also { it[1] = bar })).blocked)
        }
    }
    @Test fun unrelatedSearchControlOutsideBarDoesNotBlock() {
        val tree = explore()
        assertFalse(detector.detect(tree.copy(nodes = tree.nodes.toMutableList().also {
            it[2] = it[2].copy(depth = 1, parent = 0)
        })).blocked)
    }
    @Test fun incompleteOrOtherPackageDoesNotBlock() {
        val tree = explore()
        assertFalse(detector.detect(tree.copy(complete = false)).blocked)
        assertFalse(detector.detect(tree.copy(nodes = tree.nodes.map {
            it.copy(packageName = "com.google.android.youtube")
        })).blocked)
    }
}

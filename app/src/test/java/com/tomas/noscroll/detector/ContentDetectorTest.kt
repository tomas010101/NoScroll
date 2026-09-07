package com.tomas.noscroll.detector

import org.junit.Assert.*
import org.junit.Test

class ContentDetectorTest {
    private val instagram = InstagramReelsDetector()
    private val youtube = YouTubeShortsDetector()

    private fun node(pkg: String, id: String = "", label: String = "", depth: Int = 1,
                     visible: Boolean = true, actionable: Boolean = true,
                     left: Int = 0, top: Int = 0, right: Int = 1080, bottom: Int = 1920) =
        NodeSnapshot(if (depth == 0) -1 else depth - 1, depth, pkg, "android.view.View",
            if (id.isEmpty()) "" else "$pkg:id/$id", label, "", visible, actionable,
            left, top, right, bottom)

    private fun tree(detector: ContentDetector, labels: List<String>, player: String? = null,
                     complete: Boolean = true): TreeSnapshot {
        val pkg = detector.packageName
        return TreeSnapshot(buildList {
            add(node(pkg, depth = 0))
            if (player != null) add(node(pkg, id = player))
            labels.forEach { add(node(pkg, label = it, depth = if (player == null) 1 else 2)) }
        }, complete)
    }

    @Test fun reelsNavigationAloneIsAllowed() {
        assertFalse(instagram.detect(tree(instagram, listOf("Reels"))).blocked)
    }
    @Test fun shortsNavigationAloneIsAllowed() {
        assertFalse(youtube.detect(tree(youtube, listOf("Shorts"))).blocked)
    }
    @Test fun normalInstagramPostWithAudioIsAllowed() {
        assertFalse(instagram.detect(tree(instagram,
            listOf("Reels", "Me gusta", "Comentar", "Compartir", "Audio original"))).blocked)
    }
    @Test fun normalYouTubeVideoWithAllControlsIsAllowed() {
        assertFalse(youtube.detect(tree(youtube,
            listOf("Shorts", "Like", "Dislike", "Comments", "Share", "Remix"))).blocked)
    }
    @Test fun spanishReelIsBlocked() {
        val result = instagram.detect(tree(instagram, listOf("Reels", "Me gusta", "Comentar"),
            "clips_viewer_view_pager"))
        assertTrue(result.blocked)
        assertEquals(9, result.score)
    }
    @Test fun englishReelWithAudioIsBlocked() {
        assertTrue(instagram.detect(tree(instagram, listOf("Original audio", "Like", "Share"),
            "clips_viewer_view_pager")).blocked)
    }
    @Test fun reelSocialControlsWithoutSpecificContextAreAllowed() {
        assertFalse(instagram.detect(tree(instagram, listOf("Like", "Comment", "Share"),
            "clips_viewer_view_pager")).blocked)
    }
    @Test fun spanishShortIsBlocked() {
        assertTrue(youtube.detect(tree(youtube, listOf("Shorts", "Me gusta", "Comentarios"),
            "reel_recycler")).blocked)
    }
    @Test fun englishShortWithoutShortsLabelIsBlocked() {
        assertTrue(youtube.detect(tree(youtube, listOf("Like", "Dislike", "Comments", "Share"),
            "reel_recycler")).blocked)
    }
    @Test fun remixAndTwoControlsAreBlocked() {
        assertTrue(youtube.detect(tree(youtube, listOf("Remix", "Like", "Share"),
            "reel_recycler")).blocked)
    }
    @Test fun singleControlNeverEnoughEvenWithTwoContexts() {
        assertFalse(youtube.detect(tree(youtube, listOf("Shorts", "Remix", "Like"),
            "reel_recycler")).blocked)
    }
    @Test fun repeatedLabelsDoNotAccumulatePoints() {
        val result = youtube.detect(tree(youtube, List(15) { "Like" }, "reel_recycler"))
        assertEquals(6, result.score)
        assertFalse(result.blocked)
    }
    @Test fun dislikeDoesNotAlsoCountAsLike() {
        for (label in listOf("Dislike", "No me gusta")) {
            val result = youtube.detect(tree(youtube, listOf("Shorts", label), "reel_recycler"))
            assertFalse(result.signals.containsKey("LIKE_CONTROL"))
            assertFalse(result.blocked)
        }
    }
    @Test fun incompleteTreeIsAllowed() {
        assertFalse(youtube.detect(tree(youtube, listOf("Shorts", "Like", "Comments"),
            "reel_recycler", complete = false)).blocked)
    }
    @Test fun otherPackageIsAllowed() {
        assertFalse(instagram.detect(tree(youtube, listOf("Reels", "Like", "Comment"),
            "clips_viewer_view_pager")).blocked)
    }
    @Test fun emptyTreeIsAllowed() {
        assertFalse(youtube.detect(TreeSnapshot(emptyList(), true)).blocked)
    }
    @Test fun hiddenPlayerIsAllowed() {
        val original = tree(youtube, listOf("Shorts", "Like", "Comments"), "reel_recycler")
        val nodes = original.nodes.toMutableList()
        nodes[1] = nodes[1].copy(visible = false)
        assertFalse(youtube.detect(original.copy(nodes = nodes)).blocked)
    }
    @Test fun hiddenControlsAreIgnored() {
        val original = tree(youtube, listOf("Shorts", "Like", "Comments"), "reel_recycler")
        assertFalse(youtube.detect(original.copy(nodes = original.nodes.mapIndexed { i, n ->
            if (i >= 3) n.copy(visible = false) else n
        })).blocked)
    }
    @Test fun offscreenPlayerIsAllowed() {
        val original = tree(youtube, listOf("Shorts", "Like", "Comments"), "reel_recycler")
        val nodes = original.nodes.toMutableList()
        nodes[1] = nodes[1].copy(top = 1920, bottom = 3840)
        assertFalse(youtube.detect(original.copy(nodes = nodes)).blocked)
    }
    @Test fun smallEmbeddedPlayerIsAllowed() {
        val original = tree(youtube, listOf("Shorts", "Like", "Comments"), "reel_recycler")
        val nodes = original.nodes.toMutableList()
        nodes[1] = nodes[1].copy(bottom = 500)
        assertFalse(youtube.detect(original.copy(nodes = nodes)).blocked)
    }
    @Test fun signalsOutsidePlayerAreNotCombined() {
        val pkg = youtube.packageName
        val nodes = listOf(node(pkg, depth = 0), node(pkg, id = "reel_recycler"),
            node(pkg, label = "Shorts", depth = 2),
            node(pkg, label = "Like"), node(pkg, label = "Comments"))
        assertFalse(youtube.detect(TreeSnapshot(nodes, true)).blocked)
    }
    @Test fun exactIdsWorkWithoutLocalizedText() {
        val pkg = youtube.packageName
        val nodes = listOf(node(pkg, depth = 0), node(pkg, id = "reel_recycler")) +
            listOf("like_button", "dislike_button", "reel_comment_button", "reel_share_button")
                .map { node(pkg, id = it, depth = 2) }
        assertTrue(youtube.detect(TreeSnapshot(nodes, true)).blocked)
    }
    @Test fun labelsWithCountsWork() {
        assertTrue(youtube.detect(tree(youtube, listOf("Shorts", "Me gusta, 120", "Comentarios 25"),
            "reel_recycler")).blocked)
    }
    @Test fun captionsContainingKeywordsDoNotCount() {
        assertFalse(youtube.detect(tree(youtube,
            listOf("Shorts", "I like this video", "Share your story"), "reel_recycler")).blocked)
    }
    @Test fun oneNodeCannotCountAsTwoControls() {
        val original = tree(youtube, listOf("Shorts", "Like"), "reel_recycler")
        val nodes = original.nodes.toMutableList()
        nodes[3] = nodes[3].copy(description = "Comments")
        assertFalse(youtube.detect(original.copy(nodes = nodes)).blocked)
    }
    @Test fun nonActionableTextDoesNotCountAsControl() {
        val original = tree(youtube, listOf("Shorts", "Like", "Comments"), "reel_recycler")
        assertFalse(youtube.detect(original.copy(nodes = original.nodes.map { it.copy(actionable = false) })).blocked)
    }
}

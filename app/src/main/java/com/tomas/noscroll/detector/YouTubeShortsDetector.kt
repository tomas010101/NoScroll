package com.tomas.noscroll.detector

class YouTubeShortsDetector : ScoringDetector() {
    override val packageName = "com.google.android.youtube"
    override val playerIds = setOf(
        "reel_recycler", "reel_watch_fragment_root", "reel_watch_player",
        "shorts_player_container",
    )
    // Umbral 9: contenedor 5 + Shorts/Remix 2 + dos controles; o cuatro controles.
    // Shorts solo y los controles de videos normales jamás habilitan BACK sin contenedor.
    override val rules = listOf(
        SignalRule("SHORTS_CONTEXT", 2, setOf("shorts"), setOf("reel_player_shorts_logo")),
        SignalRule("LIKE_CONTROL", 1, setOf("like", "me gusta"),
            setOf("like_button", "reel_like_button"), control = true),
        SignalRule("DISLIKE_CONTROL", 1, setOf("dislike", "no me gusta"),
            setOf("dislike_button", "reel_dislike_button"), control = true),
        SignalRule("COMMENTS_CONTROL", 1, setOf("comments", "comentarios", "comment"),
            setOf("reel_comment_button", "comments_button"), control = true),
        SignalRule("SHARE_CONTROL", 1, setOf("share", "compartir"),
            setOf("reel_share_button", "share_button"), control = true),
        SignalRule("REMIX_CONTEXT", 2, setOf("remix", "remixar", "remezclar"),
            setOf("reel_remix_button")),
    )
}

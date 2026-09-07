package com.tomas.noscroll.detector

class InstagramReelsDetector : ScoringDetector() {
    override val packageName = "com.instagram.android"
    override val playerIds = setOf(
        "clips_viewer_view_pager", "clips_viewer_recycler_view", "clips_viewer_root",
        "clips_viewer_container",
    )
    // Umbral 9: contenedor 5 + contexto Reel o Audio 2 + al menos 2 controles.
    // Like + Comment + Share = 3: ni siquiera con un contenedor llegan a 9.
    override val rules = listOf(
        SignalRule("REELS_CONTEXT", 2, setOf("reel", "reels"), setOf("clips_viewer_title")),
        SignalRule("LIKE_CONTROL", 1, setOf("like", "me gusta"),
            setOf("clips_viewer_like_button"), control = true),
        SignalRule("COMMENTS_CONTROL", 1, setOf("comment", "comments", "comentar", "comentarios"),
            setOf("clips_viewer_comment_button"), control = true),
        SignalRule("SHARE_CONTROL", 1, setOf("share", "compartir", "send", "enviar"),
            setOf("clips_viewer_share_button"), control = true),
        SignalRule("REEL_AUDIO", 2, setOf("audio", "original audio", "audio original"),
            setOf("clips_viewer_audio_attribution", "clips_viewer_music_attribution")),
    )
}

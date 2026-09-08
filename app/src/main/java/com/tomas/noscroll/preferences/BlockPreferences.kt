package com.tomas.noscroll.preferences

import android.content.Context

class BlockPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("blocking", Context.MODE_PRIVATE)
    var instagramExplore: Boolean
        get() = prefs.getBoolean("instagram_explore", true)
        set(value) { prefs.edit().putBoolean("instagram_explore", value).apply() }
    var instagramReels: Boolean
        get() = prefs.getBoolean("instagram_reels", true)
        set(value) { prefs.edit().putBoolean("instagram_reels", value).apply() }
    var youtubeShorts: Boolean
        get() = prefs.getBoolean("youtube_shorts", true)
        set(value) { prefs.edit().putBoolean("youtube_shorts", value).apply() }

    fun enabled(packageName: String) = when (packageName) {
        "com.instagram.android" -> instagramReels
        "com.google.android.youtube" -> youtubeShorts
        else -> false
    }
}

package com.tomas.noscroll.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.tomas.noscroll.debug.AccessibilityTreeLogger
import com.tomas.noscroll.detector.InstagramReelsDetector
import com.tomas.noscroll.detector.YouTubeShortsDetector
import com.tomas.noscroll.preferences.BlockPreferences

class NoScrollAccessibilityService : AccessibilityService() {
    private val detectors = listOf(InstagramReelsDetector(), YouTubeShortsDetector()).associateBy { it.packageName }
    private val handler = Handler(Looper.getMainLooper())
    private val logger = AccessibilityTreeLogger()
    private val cooldown = BlockCooldown()
    private lateinit var preferences: BlockPreferences
    private var pendingPackage: String? = null
    private var pendingEventType = 0
    private var scheduled = false
    private var toast: Toast? = null
    private val analyze = Runnable {
        scheduled = false
        val packageName = pendingPackage
        pendingPackage = null
        if (packageName != null) inspect(packageName, pendingEventType)
    }

    override fun onCreate() {
        super.onCreate()
        preferences = BlockPreferences(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in detectors || event.eventType !in EVENT_TYPES) return
        pendingPackage = packageName
        pendingEventType = event.eventType
        // Agrupa ráfagas sin polling ni reprogramación perpetua: una lectura por lote.
        // No retenemos el AccessibilityEvent, que pertenece al framework.
        if (!scheduled) {
            scheduled = true
            handler.postDelayed(analyze, 120)
        }
    }

    private fun inspect(packageName: String, eventType: Int) {
        val detector = detectors[packageName] ?: return
        val root = rootInActiveWindow ?: return
        try {
            // Un evento puede llegar tarde después de cambiar de aplicación/ventana.
            if (root.packageName?.toString() != packageName) return
            val tree = AccessibilityTreeReader.read(root)
            logger.tree(tree)
            val result = detector.detect(tree)
            val now = SystemClock.elapsedRealtime()
            val action = when {
                !preferences.enabled(packageName) -> "DISABLED"
                !result.blocked -> "NONE"
                !cooldown.isReady(now) -> "COOLDOWN"
                else -> {
                    // Marca ANTES de BACK: los eventos generados por la acción no pueden
                    // provocar otro BACK durante 1200 ms, incluso si la acción falla.
                    cooldown.markAttempt(now)
                    if (performGlobalAction(GLOBAL_ACTION_BACK)) {
                        toast?.cancel()
                        toast = Toast.makeText(this,
                            if (packageName == "com.instagram.android") "Reels bloqueados por NoScroll"
                            else "Shorts bloqueados por NoScroll", Toast.LENGTH_SHORT).also { it.show() }
                        "BACK"
                    } else "BACK_FAILED"
                }
            }
            logger.decision(packageName, eventType, result, action)
        } finally {
            AccessibilityTreeReader.release(root)
        }
    }

    override fun onInterrupt() { clearPending() }
    override fun onDestroy() {
        clearPending()
        super.onDestroy()
    }
    private fun clearPending() {
        handler.removeCallbacks(analyze)
        scheduled = false
        pendingPackage = null
        toast?.cancel()
        toast = null
    }

    companion object {
        private val EVENT_TYPES = setOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_VIEW_SCROLLED)
    }
}

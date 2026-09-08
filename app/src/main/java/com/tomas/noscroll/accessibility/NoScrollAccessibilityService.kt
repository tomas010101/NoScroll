package com.tomas.noscroll.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.tomas.noscroll.debug.AccessibilityTreeLogger
import com.tomas.noscroll.detector.InstagramReelsDetector
import com.tomas.noscroll.detector.InstagramExploreDetector
import com.tomas.noscroll.detector.YouTubeShortsDetector
import com.tomas.noscroll.preferences.BlockPreferences

class NoScrollAccessibilityService : AccessibilityService() {
    private val detectors = listOf(InstagramReelsDetector(), YouTubeShortsDetector()).associateBy { it.packageName }
    private val exploreDetector = InstagramExploreDetector()
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
        logger.stage("CREATE", "service=$packageName", always = true)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        logger.stage("CONNECTED", "packages=${serviceInfo.packageNames?.joinToString()} " +
            "eventTypes=${serviceInfo.eventTypes} flags=${serviceInfo.flags}", always = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        logger.stage("EVENT", "package=$packageName event=${AccessibilityEvent.eventTypeToString(event.eventType)}")
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
        val root = rootInActiveWindow
        logger.stage("ROOT", "package=$packageName event=${AccessibilityEvent.eventTypeToString(eventType)} " +
            "null=${root == null} rootPackage=${root?.packageName}")
        if (root == null) return
        try {
            // Un evento puede llegar tarde después de cambiar de aplicación/ventana.
            if (root.packageName?.toString() != packageName) return
            val tree = AccessibilityTreeReader.read(root)
            logger.stage("SNAPSHOT", "package=$packageName nodes=${tree.nodes.size} complete=${tree.complete}")
            logger.tree(tree)
            val exploreResult = if (packageName == exploreDetector.packageName) exploreDetector.detect(tree) else null
            exploreResult?.let {
                logger.stage("EXPLORE", "score=${it.score} signals=${it.signals} reason=${it.reason} " +
                    "enabled=${preferences.instagramExplore}")
            }
            val blockingExplore = exploreResult?.blocked == true
            val result = if (blockingExplore) requireNotNull(exploreResult) else detector.detect(tree)
            val enabled = if (blockingExplore) preferences.instagramExplore else preferences.enabled(packageName)
            val now = SystemClock.elapsedRealtime()
            val action = when {
                !enabled -> "DISABLED"
                !result.blocked -> "NONE"
                !cooldown.isReady(now) -> "COOLDOWN"
                else -> {
                    // Marca ANTES de BACK: los eventos generados por la acción no pueden
                    // provocar otro BACK durante 1200 ms, incluso si la acción falla.
                    cooldown.markAttempt(now)
                    logger.stage("BACK_CALL", "package=$packageName score=${result.score}", always = true)
                    val backAccepted = performGlobalAction(GLOBAL_ACTION_BACK)
                    logger.stage("BACK_RESULT", "package=$packageName returned=$backAccepted", always = true)
                    if (backAccepted) {
                        toast?.cancel()
                        toast = Toast.makeText(this,
                            if (blockingExplore) "Explorar bloqueado por NoScroll"
                            else if (packageName == "com.instagram.android") "Reels bloqueados por NoScroll"
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

    override fun onInterrupt() {
        logger.stage("INTERRUPT", "service=$packageName", always = true)
        clearPending()
    }
    override fun onDestroy() {
        logger.stage("DESTROY", "service=$packageName", always = true)
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
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_CLICKED, AccessibilityEvent.TYPE_VIEW_SELECTED)
    }
}

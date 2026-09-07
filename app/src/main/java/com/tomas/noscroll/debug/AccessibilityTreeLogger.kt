package com.tomas.noscroll.debug

import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.tomas.noscroll.BuildConfig
import com.tomas.noscroll.detector.DetectionResult
import com.tomas.noscroll.detector.TreeSnapshot

class AccessibilityTreeLogger {
    private var lastTreeAt: Long? = null
    private var lastDecisionAt: Long? = null
    private var lastDecision = ""

    fun tree(tree: TreeSnapshot) {
        if (!BuildConfig.DEBUG || tree.packageName !in PACKAGES) return
        val now = SystemClock.elapsedRealtime()
        if (lastTreeAt?.let { now - it < 5_000 } == true) return
        lastTreeAt = now
        Log.d(TREE_TAG, "package=${tree.packageName} nodes=${tree.nodes.size} complete=${tree.complete}")
        // Se reutiliza el snapshot acotado, sin un segundo recorrido del framework.
        tree.nodes.take(120).forEachIndexed { index, n ->
            Log.d(TREE_TAG, "#$index parent=${n.parent} depth=${n.depth} visible=${n.visible} " +
                "bounds=${n.left},${n.top},${n.right},${n.bottom} " +
                "className=${safe(n.className)} viewIdResourceName=${safe(n.viewId)} " +
                "text=${safe(n.text)} contentDescription=${safe(n.description)}")
        }
        if (tree.nodes.size > 120) Log.d(TREE_TAG, "Log limitado a 120 nodos")
    }

    fun decision(packageName: String, eventType: Int, result: DetectionResult, action: String) {
        if (!BuildConfig.DEBUG || packageName !in PACKAGES) return
        val now = SystemClock.elapsedRealtime()
        val key = "$packageName|${result.signals}|${result.reason}|$action"
        // Cambios relevantes como máximo 5/s; decisiones idénticas una vez cada 2s.
        val elapsed = lastDecisionAt?.let { now - it } ?: Long.MAX_VALUE
        if (elapsed < 200 || (key == lastDecision && elapsed < 2_000)) return
        lastDecision = key
        lastDecisionAt = now
        Log.d(DETECTOR_TAG, "package=$packageName event=${AccessibilityEvent.eventTypeToString(eventType)} " +
            "detector=${result.detector} score=${result.score} threshold=${result.threshold} " +
            "signals=${result.signals} result=${if (result.blocked) "BLOCK" else "ALLOW"} " +
            "reason=${result.reason} action=$action")
    }

    private fun safe(text: String) = text.take(160).replace('\n', ' ').replace('\r', ' ')

    companion object {
        const val TREE_TAG = "NOSCROLL_TREE"
        const val DETECTOR_TAG = "NOSCROLL_DETECTOR"
        private val PACKAGES = setOf("com.instagram.android", "com.google.android.youtube")
    }
}

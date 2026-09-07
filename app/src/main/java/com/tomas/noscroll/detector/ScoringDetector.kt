package com.tomas.noscroll.detector

import java.text.Normalizer
import java.util.Locale

data class SignalRule(
    val name: String,
    val points: Int,
    val labels: Set<String> = emptySet(),
    val ids: Set<String> = emptySet(),
    val control: Boolean = false,
)

/** Sólo IDs exactos de contenedores de reproducción, nunca IDs de pestañas o estantes.
 * Se puntúa un único subárbol visible: no se suman señales de tarjetas independientes.
 * Los IDs son heurísticos y deben contrastarse con Logcat del teléfono; pueden cambiar.
 */
abstract class ScoringDetector : ContentDetector {
    abstract val playerIds: Set<String>
    abstract val rules: List<SignalRule>
    open val threshold = 9
    open val playerPoints = 5
    open val minimumControls = 2
    open val minimumHeightRatio = 0.65
    open val minimumWidthRatio = 0.65

    override fun detect(tree: TreeSnapshot): DetectionResult {
        fun result(signals: Map<String, Int>, blocked: Boolean, reason: String) = DetectionResult(
            javaClass.simpleName, signals.values.sum(), threshold, signals, blocked, reason)
        if (tree.packageName != packageName) return result(emptyMap(), false, "WRONG_PACKAGE")
        if (!tree.complete) return result(emptyMap(), false, "INCOMPLETE_TREE")
        val root = tree.nodes.firstOrNull() ?: return result(emptyMap(), false, "EMPTY_TREE")
        val candidates = tree.nodes.indices.filter { index ->
            val n = tree.nodes[index]
            n.visible && n.packageName == packageName &&
                n.viewId.substringAfter(":id/", "") in playerIds &&
                root.width > 0 && root.height > 0 &&
                n.visibleWidth(root) >= root.width * minimumWidthRatio &&
                n.visibleHeight(root) >= root.height * minimumHeightRatio
        }
        var best = result(emptyMap(), false, "NO_PLAYER_CONTAINER")
        for (index in candidates) {
            val scope = tree.visibleSubtree(index)
            val signals = linkedMapOf("PLAYER_CONTAINER" to playerPoints)
            val controlNodes = mutableSetOf<Int>()
            for (rule in rules) {
                val match = scope.indices.firstOrNull {
                    (!rule.control || it !in controlNodes) && matches(scope[it], rule)
                }
                if (match != null) {
                    signals[rule.name] = rule.points
                    // Un nodo no puede hacerse pasar por dos controles diferentes.
                    if (rule.control) controlNodes += match
                }
            }
            val block = signals.values.sum() >= threshold && controlNodes.size >= minimumControls
            val decision = result(signals, block,
                if (block) "PLAYER_AND_MULTIPLE_SIGNALS" else "INSUFFICIENT_SIGNALS")
            if (decision.blocked || decision.score > best.score) best = decision
            if (best.blocked) break
        }
        return best
    }

    private fun matches(node: NodeSnapshot, rule: SignalRule): Boolean {
        val id = node.viewId.substringAfter(":id/", "")
        if (id in rule.ids) return true
        if (rule.control && !node.actionable) return false
        return sequenceOf(node.text, node.description).any { raw ->
            val value = normalize(raw)
            // Evita que "Dislike" o "No me gusta" cuenten también como Like.
            if (rule.name == "LIKE_CONTROL" &&
                (value.contains("dislike") || value.contains("no me gusta"))) false
            else rule.labels.any { label ->
                // Etiquetas completas o prefijos separados por puntuación/cantidad.
                // No buscamos subcadenas dentro de captions o títulos arbitrarios.
                value == label || value.startsWith("$label,") ||
                    value.startsWith("$label.") || value.startsWith("$label:") ||
                    (value.startsWith("$label ") && value.drop(label.length + 1).firstOrNull()?.isDigit() == true)
            }
        }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
}

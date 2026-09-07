package com.tomas.noscroll.accessibility

/** Reloj monotónico inyectado: cambiar la hora del teléfono no altera el debounce. */
class BlockCooldown(private val durationMs: Long = 1_200) {
    private var lastAttempt: Long? = null
    fun isReady(now: Long): Boolean = lastAttempt?.let { now - it >= durationMs } ?: true
    fun markAttempt(now: Long) { lastAttempt = now }
}

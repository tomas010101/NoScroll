package com.tomas.noscroll.accessibility

import org.junit.Assert.*
import org.junit.Test

class BlockCooldownTest {
    @Test fun firstEventCanBlockEvenJustAfterBoot() {
        assertTrue(BlockCooldown().isReady(100))
    }
    @Test fun backEventsAreSuppressedFor1200Milliseconds() {
        val cooldown = BlockCooldown()
        cooldown.markAttempt(100)
        assertFalse(cooldown.isReady(100))
        assertFalse(cooldown.isReady(1299))
        assertTrue(cooldown.isReady(1300))
        cooldown.markAttempt(1300)
        assertFalse(cooldown.isReady(1400))
    }
}

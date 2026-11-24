package com.xelilovth.nota.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackServiceTest {
    @Test
    fun playerErrorStartsForegroundBeforeStoppingService() {
        val calls = mutableListOf<String>()

        PlaybackService().handlePlayerError(
            startForegroundAction = { calls += "startForeground" },
            stopServiceAction = { calls += "stopSelf" },
        )

        assertEquals(listOf("startForeground", "stopSelf"), calls)
    }
}

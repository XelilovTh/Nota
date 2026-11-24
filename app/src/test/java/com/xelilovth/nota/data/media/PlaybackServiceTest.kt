package com.xelilovth.nota.data.media

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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

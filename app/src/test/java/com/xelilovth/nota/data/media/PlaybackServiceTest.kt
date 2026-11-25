package com.xelilovth.nota.data.media

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

    @Test
    fun onStartCommandImmediatelyEntersForeground() {
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        val service = controller.get()

        service.onStartCommand(Intent(service, PlaybackService::class.java), 0, 1)

        assertNotNull(shadowOf(service).lastForegroundNotification)
        controller.destroy()
    }
}

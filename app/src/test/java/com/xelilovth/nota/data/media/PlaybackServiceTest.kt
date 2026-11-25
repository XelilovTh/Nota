package com.xelilovth.nota.data.media

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackServiceTest {
    @After
    fun tearDown() {
        PlaybackPlayerHolder.release()
    }

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
    fun emptyQueueDoesNotCreateAnEmptyForegroundNotification() {
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        val service = controller.get()
        PlaybackPlayerHolder.get(service.applicationContext).clearMediaItems()

        service.onStartCommand(Intent(service, PlaybackService::class.java), 0, 1)

        assertEquals(null, shadowOf(service).lastForegroundNotification)
        controller.destroy()
    }
}

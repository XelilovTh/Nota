package com.xelilovth.nota.data.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class PlaybackPlayerHolderTest {

    @After
    fun tearDown() {
        PlaybackPlayerHolder.release()
    }

    @Test
    fun getInitializesPlayerWithAudioAttributesForMusicPlayback() {
        val context = RuntimeEnvironment.getApplication()
        val player = PlaybackPlayerHolder.get(context)

        assertNotNull(player)
        assertEquals(C.USAGE_MEDIA, player.audioAttributes.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_MUSIC, player.audioAttributes.contentType)
    }

    @Test
    fun releaseDestroysInstanceAndSubsequentGetCreatesNewOne() {
        val context = RuntimeEnvironment.getApplication()
        val player1 = PlaybackPlayerHolder.get(context)

        PlaybackPlayerHolder.release()

        val player2 = PlaybackPlayerHolder.get(context)
        assertNotSame(player1, player2)
    }
}

package com.xelilovth.nota.data.media

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val playerErrorListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = PlaybackPlayerHolder.get(applicationContext)
        player.addListener(playerErrorListener)
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        PlaybackPlayerHolder.get(applicationContext).removeListener(playerErrorListener)
        mediaSession?.release()
        mediaSession = null
        // The application-scoped player is shared with PlaybackRepositoryImpl.
        // A service restart must not release the instance used by the repository.
        super.onDestroy()
    }
}

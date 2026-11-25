package com.xelilovth.nota.data.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.xelilovth.nota.R

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val playerErrorListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.notification_channel_playback)
                .setNotificationId(NOTIFICATION_ID)
                .build(),
        )
        val player = PlaybackPlayerHolder.get(applicationContext)
        player.addListener(playerErrorListener)
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification(getString(R.string.notification_channel_playback)))
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    internal fun handlePlayerError(
        startForegroundAction: () -> Unit = { startForegroundForError() },
        stopServiceAction: () -> Unit = { stopSelf() },
    ) {
        startForegroundAction()
        stopServiceAction()
    }

    private fun startForegroundForError() {
        startForegroundCompat(buildNotification(getString(R.string.playback_error_notification)))
    }

    private fun buildNotification(contentText: String): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .build()

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_playback),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        PlaybackPlayerHolder.get(applicationContext).removeListener(playerErrorListener)
        mediaSession?.release()
        mediaSession = null
        // The application-scoped player is shared with PlaybackRepositoryImpl.
        // A service restart must not release the instance used by the repository.
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "nota_media_playback"
        private const val NOTIFICATION_ID = 1001
    }
}

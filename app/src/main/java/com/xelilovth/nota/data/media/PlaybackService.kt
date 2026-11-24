package com.xelilovth.nota.data.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
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
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    internal fun handlePlayerError(
        startForegroundAction: () -> Unit = { startForegroundForError() },
        stopServiceAction: () -> Unit = { stopSelf() },
    ) {
        try {
            startForegroundAction()
        } catch (e: Exception) {
            Log.e(TAG, "Failed startForegroundAction in handlePlayerError", e)
        }
        try {
            stopServiceAction()
        } catch (e: Exception) {
            Log.e(TAG, "Failed stopServiceAction in handlePlayerError", e)
        }
    }

    private fun startForegroundForError() {
        try {
            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.playback_error_notification))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(false)
                .build()
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground for error", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_playback),
                    NotificationManager.IMPORTANCE_LOW,
                )
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create notification channel", e)
            }
        }
    }

    override fun onDestroy() {
        try {
            PlaybackPlayerHolder.get(applicationContext).removeListener(playerErrorListener)
            mediaSession?.release()
            mediaSession = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
        // The application-scoped player is shared with PlaybackRepositoryImpl.
        // A service restart must not release the instance used by the repository.
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val NOTIFICATION_CHANNEL_ID = "nota_media_playback"
        private const val NOTIFICATION_ID = 1001
    }
}

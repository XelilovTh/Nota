package com.xelilovth.nota.data.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.xelilovth.nota.MainActivity
import com.xelilovth.nota.R

@OptIn(UnstableApi::class)
class PlaybackService : Service() {
    private var mediaSession: MediaSession? = null
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateNotification()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateNotification()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateNotification()
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val player = PlaybackPlayerHolder.get(applicationContext)
        player.addListener(playerListener)
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = PlaybackPlayerHolder.get(applicationContext)
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) {
                    player.pause()
                } else if (player.mediaItemCount > 0) {
                    player.play()
                }
                updateNotification()
            }
            ACTION_NEXT -> {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                }
                updateNotification()
            }
            ACTION_PREVIOUS -> {
                PlaybackNavigation.skipPrevious(player)
                updateNotification()
            }
            ACTION_STOP -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                if (player.mediaItemCount == 0) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                } else {
                    startForegroundWithNotification(buildMediaNotification(player))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    internal fun handlePlayerError(
        startForegroundAction: () -> Unit = {
            startForegroundWithNotification(buildErrorNotification())
        },
        stopServiceAction: () -> Unit = { stopSelf() },
    ) {
        startForegroundAction()
        stopServiceAction()
    }

    private fun updateNotification() {
        val player = mediaSession?.player ?: return
        if (player.mediaItemCount == 0) return
        val notification = buildMediaNotification(player)
        if (player.isPlaying || player.playWhenReady) {
            startForegroundWithNotification(notification)
        } else {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildMediaNotification(player: Player): Notification {
        val metadata = player.currentMediaItem?.mediaMetadata
        val title = metadata?.title?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name)
        val artist = metadata?.artist?.takeIf { it.isNotBlank() } ?: getString(R.string.notification_channel_playback)
        val album = metadata?.albumTitle?.takeIf { it.isNotBlank() }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val prevPendingIntent = serviceActionPendingIntent(ACTION_PREVIOUS, 1)
        val playPausePendingIntent = serviceActionPendingIntent(ACTION_PLAY_PAUSE, 2)
        val nextPendingIntent = serviceActionPendingIntent(ACTION_NEXT, 3)

        val isPlaying = player.isPlaying || player.playWhenReady
        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .apply { if (album != null) setSubText(album) }
            .setContentIntent(openAppIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, playPauseLabel, playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)

        mediaSession?.let { session ->
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2),
            )
        }

        return builder.build()
    }

    private fun buildErrorNotification(): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.playback_error_notification))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .build()

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun startForegroundWithNotification(notification: Notification) {
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
            ).apply {
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        PlaybackPlayerHolder.get(applicationContext).removeListener(playerListener)
        mediaSession?.release()
        mediaSession = null
        // The application-scoped player is shared with PlaybackRepositoryImpl.
        // A service restart must not release the instance used by the repository.
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "nota_media_playback"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.xelilovth.nota.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.xelilovth.nota.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.xelilovth.nota.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.xelilovth.nota.ACTION_STOP"
        const val ACTION_UPDATE = "com.xelilovth.nota.ACTION_UPDATE"
    }
}

package com.xelilovth.nota.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.xelilovth.nota.MainActivity
import com.xelilovth.nota.R
import com.xelilovth.nota.data.media.PlaybackService

class NotaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateWidgets(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetIds = appWidgetIds,
            title = null,
            artist = null,
            isPlaying = false,
        )
    }

    companion object {
        private const val REQUEST_CODE_OPEN_APP = 100
        private const val REQUEST_CODE_PREV = 101
        private const val REQUEST_CODE_PLAY_PAUSE = 102
        private const val REQUEST_CODE_NEXT = 103

        fun updateWidgets(
            context: Context,
            title: String?,
            artist: String?,
            isPlaying: Boolean,
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, NotaWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) return

            updateWidgets(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetIds = appWidgetIds,
                title = title,
                artist = artist,
                isPlaying = isPlaying,
            )
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            title: String?,
            artist: String?,
            isPlaying: Boolean,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_nota).apply {
                setTextViewText(
                    R.id.widget_title,
                    title?.takeIf { it.isNotBlank() } ?: "Nota",
                )
                setTextViewText(
                    R.id.widget_artist,
                    artist?.takeIf { it.isNotBlank() } ?: "Ready to play",
                )
                setImageViewResource(
                    R.id.widget_play_pause,
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                )

                val openAppIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE_OPEN_APP,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                setOnClickPendingIntent(R.id.widget_root, openAppIntent)

                setOnClickPendingIntent(
                    R.id.widget_prev,
                    serviceActionPendingIntent(context, PlaybackService.ACTION_PREVIOUS, REQUEST_CODE_PREV),
                )
                setOnClickPendingIntent(
                    R.id.widget_play_pause,
                    serviceActionPendingIntent(context, PlaybackService.ACTION_PLAY_PAUSE, REQUEST_CODE_PLAY_PAUSE),
                )
                setOnClickPendingIntent(
                    R.id.widget_next,
                    serviceActionPendingIntent(context, PlaybackService.ACTION_NEXT, REQUEST_CODE_NEXT),
                )
            }

            appWidgetManager.updateAppWidget(appWidgetIds, views)
        }

        private fun serviceActionPendingIntent(
            context: Context,
            action: String,
            requestCode: Int,
        ): PendingIntent {
            val intent = Intent(context, PlaybackService::class.java).apply {
                this.action = action
            }
            return PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}

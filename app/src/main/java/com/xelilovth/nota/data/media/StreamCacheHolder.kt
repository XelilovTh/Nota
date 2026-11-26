package com.xelilovth.nota.data.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.io.IOException

@OptIn(UnstableApi::class)
object StreamCacheHolder {
    private const val CACHE_DIR_NAME = "media_stream_cache"
    private const val ONLINE_URI_PREFIX = "nota://online/"

    @Volatile
    var onlineUriResolver: ((String) -> String?)? = null

    @Volatile
    private var cacheInstance: SimpleCache? = null

    fun getCache(context: Context): SimpleCache =
        cacheInstance ?: synchronized(this) {
            cacheInstance ?: run {
                val appContext = context.applicationContext
                val prefs = appContext.getSharedPreferences("nota_settings", Context.MODE_PRIVATE)
                val configuredMb = prefs.getInt("stream_cache_size_mb", 500).coerceIn(100, 2048)
                val maxBytes = configuredMb * 1024L * 1024L
                val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
                SimpleCache(
                    cacheDir,
                    LeastRecentlyUsedCacheEvictor(maxBytes),
                    StandaloneDatabaseProvider(appContext),
                ).also { cacheInstance = it }
            }
        }

    fun createDataSourceFactory(context: Context): DataSource.Factory {
        val appContext = context.applicationContext
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val upstreamFactory = DefaultDataSource.Factory(appContext, httpFactory)
        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
            val rawUri = dataSpec.uri.toString()
            if (rawUri.startsWith(ONLINE_URI_PREFIX)) {
                val resolved = onlineUriResolver?.invoke(rawUri)
                    ?: throw IOException("Unable to resolve online stream URI: $rawUri")
                dataSpec.withUri(Uri.parse(resolved))
            } else {
                dataSpec
            }
        }
        return CacheDataSource.Factory()
            .setCache(getCache(appContext))
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun getCacheSizeBytes(context: Context): Long =
        cacheInstance?.cacheSpace ?: run {
            val dir = File(context.applicationContext.cacheDir, CACHE_DIR_NAME)
            if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }

    fun clearCache(context: Context) {
        synchronized(this) {
            runCatching {
                cacheInstance?.release()
                cacheInstance = null
                File(context.applicationContext.cacheDir, CACHE_DIR_NAME).deleteRecursively()
            }
        }
    }
}

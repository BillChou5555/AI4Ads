package com.aiads.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import java.io.File
class PlayerPool(private val context: Context, private val maxSize: Int = 3) {

    private val idlePlayers = ArrayDeque<ExoPlayer>()
    private val busyPlayers = mutableSetOf<ExoPlayer>()
    private var totalCreated = 0
    /** 视频磁盘缓存（所有播放器共享，最多 200MB） */
    private val videoCache: SimpleCache by lazy {
        val cacheDir = File(context.cacheDir, "exoplayer_cache")
        SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024))
    }

    fun acquire(): ExoPlayer {
        val player = when {
            idlePlayers.isNotEmpty() -> idlePlayers.removeFirst()
            totalCreated < maxSize -> {
                totalCreated++
                // 创建带缓存的播放器
                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(videoCache)
                    .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
                ExoPlayer.Builder(context)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
                    .build()
                    .apply { volume = 0f }
            }
            else -> {
                // 池满：回收 busy 中最旧的播放器
                val oldest = busyPlayers.first()
                release(oldest)
                oldest
            }
        }
        player.volume = 0f  // 所有出口统一重置静音
        player.repeatMode = Player.REPEAT_MODE_ALL
        busyPlayers.add(player)
        return player
    }

    fun release(player: ExoPlayer) {
        player.stop()
        player.clearMediaItems()
        busyPlayers.remove(player)
        idlePlayers.addLast(player)
    }

    fun releaseAll() {
        busyPlayers.toList().forEach { release(it) }
    }
}
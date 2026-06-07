package com.aiads.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class PlayerPool(private val context: Context, private val maxSize: Int = 3) {

    private val idlePlayers = ArrayDeque<ExoPlayer>()
    private val busyPlayers = mutableSetOf<ExoPlayer>()
    private var totalCreated = 0

    fun acquire(): ExoPlayer {
        val player = when {
            idlePlayers.isNotEmpty() -> idlePlayers.removeFirst()
            totalCreated < maxSize -> {
                totalCreated++
                ExoPlayer.Builder(context).build().apply {
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ALL
                }
            }
            else -> {
                // 池满：回收 busy 中最旧的播放器
                val oldest = busyPlayers.first()
                release(oldest)
                oldest
            }
        }
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
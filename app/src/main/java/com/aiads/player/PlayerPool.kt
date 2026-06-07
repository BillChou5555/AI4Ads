package com.aiads.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class PlayerPool(private val context: Context, private val maxSize: Int = 3) {

    private val idlePlayers = ArrayDeque<ExoPlayer>()
    private val busyPlayers = mutableSetOf<ExoPlayer>()

    fun acquire(): ExoPlayer {
        val player = if (idlePlayers.isNotEmpty()) {
            idlePlayers.removeFirst()
        } else {
            ExoPlayer.Builder(context).build().apply {
                volume = 0f              // 默认静音
                repeatMode = Player.REPEAT_MODE_ALL
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
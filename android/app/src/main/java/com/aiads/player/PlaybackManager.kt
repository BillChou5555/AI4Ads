package com.aiads.player

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.RecyclerView
import com.aiads.ui.feed.cards.VideoCardAdapter
import kotlin.math.abs

class PlaybackManager(
    private val playerPool: PlayerPool,
    private val recyclerView: RecyclerView
) {
    private var activeViewHolder: VideoCardAdapter.ViewHolder? = null
    private var activePlayer: ExoPlayer? = null
    private var isAttached = false

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            // 在停止滚动时计算位置，节省CPU资源
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                evaluate()
            }
        }
    }

    fun attach() {
        if (isAttached) return
        recyclerView.addOnScrollListener(scrollListener)
        isAttached = true
    }

    fun detach() {
        // 归还当前播放器
        activePlayer?.let { playerPool.release(it) }
        activePlayer = null
        activeViewHolder = null
    }

    fun destroy() {
        // Fragment onDestroy 时彻底清理
        recyclerView.removeOnScrollListener(scrollListener)
        isAttached = false
        detach()
    }

    fun evaluate() {
        val screenCenter = recyclerView.height / 2
        val range = (recyclerView.height * 0.25f).toInt()  // ±25% 核心区域

        var bestViewHolder: VideoCardAdapter.ViewHolder? = null
        var bestDistance = Int.MAX_VALUE

        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val holder = recyclerView.getChildViewHolder(child)
            if (holder is VideoCardAdapter.ViewHolder) {
                val cardCenterY = (child.top + child.bottom) / 2
                val distance = abs(cardCenterY - screenCenter)
                if (distance < bestDistance && distance <= range) {
                    bestDistance = distance
                    bestViewHolder = holder
                }
            }
        }

        // 无变化则跳过
        if (bestViewHolder == activeViewHolder) return

        // 归还旧播放器
        activePlayer?.let { playerPool.release(it) }
        activeViewHolder?.detachPlayer()

        // 分配新播放器
        if (bestViewHolder != null) {
            val player = playerPool.acquire()
            bestViewHolder.attachPlayer(player)
            player.play()
            activePlayer = player
        } else {
            activePlayer = null
        }
        activeViewHolder = bestViewHolder
    }
}
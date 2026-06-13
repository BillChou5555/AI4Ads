package com.aiads.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aiads.R
import com.bumptech.glide.Glide
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy

/**
 * 详情页媒体 HorizontalViewPager2 的适配器。
 *
 * ## ViewType 判定
 * MediaItem 是 sealed class，用 when 分支判断 Image/Video 即可，
 * 不需要手动定义 type 常量。
 */

class MediaPagerAdapter(
    private val items: List<MediaItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is MediaItem.Image -> 0
            is MediaItem.Video -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            0 -> ImageViewHolder(parent)
            1 -> VideoViewHolder(parent)
            else -> throw IllegalStateException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is MediaItem.Image -> (holder as ImageViewHolder).bind(item.url)
            is MediaItem.Video -> (holder as VideoViewHolder).bind(item.url)
        }
    }
}

class ImageViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.item_media_image, parent, false)
) {

    fun bind(imageUrl: String) {
        Glide.with(itemView)
            .load(imageUrl)
            .priority(Priority.IMMEDIATE)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.placeholder_image)
            .into(itemView.findViewById(R.id.ivMedia))
    }
}

class VideoViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.item_media_video, parent, false)
) {
    val playerView: PlayerView = itemView.findViewById(R.id.playerView)

    fun bind(videoUrl: String) {
        // 播放器由 DetailFragment 通过 OnPageChangeCallback 管理
        // 这里仅提供 PlayerView 容器，playerView.tag 存储 videoUrl 供外部使用
        playerView.tag = videoUrl
    }
}

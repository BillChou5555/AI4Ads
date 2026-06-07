package com.aiads.ui.feed.cards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.aiads.R
import com.aiads.data.local.DatabaseHelper
import com.aiads.data.model.Ad
import com.bumptech.glide.Glide
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class VideoCardAdapter(
    private val onCardClick: (Ad) -> Unit,
    private val onTagClick: (String) -> Unit,
    private val onLikeClick: (Ad) -> Unit,
    private val onBookmarkClick: (Ad) -> Unit,
    private val onShareClick: (Ad) -> Unit
) : ListAdapter<Ad, VideoCardAdapter.ViewHolder>(DiffCallback) {
    var interactionMap: Map<String, DatabaseHelper.InteractionState> = emptyMap()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ad = getItem(position)
        holder.bind(
            ad = ad,
            interaction = interactionMap[ad.adId],
            onCardClick = onCardClick,
            onTagClick = onTagClick,
            onLikeClick = onLikeClick,
            onBookmarkClick = onBookmarkClick,
            onShareClick = onShareClick
        )
    }

    class ViewHolder(itemView: android.view.View) : BaseCardViewHolder(itemView) {
        private var currentAd: Ad? = null
        private val ivVideoCover: ImageView = itemView.findViewById(R.id.ivVideoCover)
        private val ivMuteIcon: ImageView = itemView.findViewById(R.id.ivMuteIcon)
        private val ivPlayIcon: ImageView = itemView.findViewById(R.id.ivPlayIcon)

        override fun bindMedia(ad: Ad) {
            currentAd = ad
            // M6: 加载视频封面图
            if (ad.adImages.isNotEmpty()) {
                Glide.with(ivVideoCover.context)
                    .load(ad.adImages[0])
                    .into(ivVideoCover)
            }
        }
        fun attachPlayer(player: ExoPlayer) {
            val videoUrl = currentAd?.adVideoUrl ?: return
            val playerView = itemView.findViewById<androidx.media3.ui.PlayerView>(R.id.playerView)
            playerView.player = player
            player.setMediaItem(MediaItem.fromUri(videoUrl))
            player.prepare()
            ivVideoCover.visibility = View.GONE  // 隐藏封面
            ivPlayIcon.visibility = View.GONE        // 隐藏播放按钮
            ivMuteIcon.visibility = View.VISIBLE     // 显示静音图标
        }

        fun detachPlayer() {
            val playerView = itemView.findViewById<androidx.media3.ui.PlayerView>(R.id.playerView)
            playerView.player = null
            ivVideoCover.visibility = View.VISIBLE  // 恢复封面
            ivPlayIcon.visibility = View.VISIBLE
            ivMuteIcon.visibility = View.GONE
        }

    }



    companion object DiffCallback : DiffUtil.ItemCallback<Ad>() {
        override fun areItemsTheSame(oldItem: Ad, newItem: Ad): Boolean =
            oldItem.adId == newItem.adId

        override fun areContentsTheSame(oldItem: Ad, newItem: Ad): Boolean =
            oldItem == newItem
    }
}
package com.aiads.ui.feed.cards

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.aiads.R
import com.aiads.data.local.DatabaseHelper
import com.aiads.data.model.Ad

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
        private val ivVideoCover: ImageView = itemView.findViewById(R.id.ivVideoCover)
        private val ivMuteIcon: ImageView = itemView.findViewById(R.id.ivMuteIcon)
        private val ivPlayIcon: ImageView = itemView.findViewById(R.id.ivPlayIcon)

        override fun bindMedia(ad: Ad) {
            // M4 将替换为 ExoPlayer PlayerView
            // M3 阶段：显示加载封面图 + 播放/静音图标
            // 静音图标默认可见（Feed 流视频自动静音）
            ivMuteIcon.visibility = android.view.View.VISIBLE
            ivPlayIcon.visibility = android.view.View.VISIBLE
            // M6 将替换为 Glide 加载 ad.adImages.first() 作为封面
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Ad>() {
        override fun areItemsTheSame(oldItem: Ad, newItem: Ad): Boolean =
            oldItem.adId == newItem.adId

        override fun areContentsTheSame(oldItem: Ad, newItem: Ad): Boolean =
            oldItem == newItem
    }
}
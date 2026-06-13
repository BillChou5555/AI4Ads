package com.aiads.ui.feed.cards

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.aiads.R
import com.aiads.data.local.DatabaseHelper
import com.aiads.data.model.Ad
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
class TextOnlyCardAdapter(
    private val onCardClick: (Ad) -> Unit,
    private val onTagClick: (String) -> Unit,
    private val onLikeClick: (Ad) -> Unit,
    private val onBookmarkClick: (Ad) -> Unit,
    private val onShareClick: (Ad) -> Unit
) : ListAdapter<Ad, TextOnlyCardAdapter.ViewHolder>(DiffCallback) {
    var interactionMap: Map<String, DatabaseHelper.InteractionState> = emptyMap()
    var activeFilterTags: Set<String> = emptySet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card_text_only, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ad = getItem(position)
        holder.bind(
            ad = ad,
            interaction = interactionMap[ad.adId],
            activeFilterTags = activeFilterTags,
            onCardClick = onCardClick,
            onTagClick = onTagClick,
            onLikeClick = onLikeClick,
            onBookmarkClick = onBookmarkClick,
            onShareClick = onShareClick
        )
    }

    class ViewHolder(itemView: android.view.View) : BaseCardViewHolder(itemView) {

        override fun bindMedia(ad: Ad) {
            // 纯文字卡片没有媒体区域——这就是为什么 bindMedia 的实现是空的
            // 标题和摘要的 maxLines 调大：纯文字卡片空间更多
            tvTitle.maxLines = 3
            tvAiSummary.maxLines = 3
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Ad>() {
        override fun areItemsTheSame(oldItem: Ad, newItem: Ad): Boolean =
            oldItem.adId == newItem.adId

        override fun areContentsTheSame(oldItem: Ad, newItem: Ad): Boolean =
            oldItem == newItem
    }
}
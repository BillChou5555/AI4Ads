package com.aiads.ui.feed.cards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.aiads.R
import com.aiads.data.local.DatabaseHelper
import com.aiads.data.model.Ad
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
class MultiImageCardAdapter(
    private val onCardClick: (Ad) -> Unit,
    private val onTagClick: (String) -> Unit,
    private val onLikeClick: (Ad) -> Unit,
    private val onBookmarkClick: (Ad) -> Unit,
    private val onShareClick: (Ad) -> Unit
) : ListAdapter<Ad, MultiImageCardAdapter.ViewHolder>(DiffCallback) {
    var interactionMap: Map<String, DatabaseHelper.InteractionState> = emptyMap()
    var activeFilterTags: Set<String> = emptySet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card_multi_image, parent, false)
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
        private val ivImage1: ImageView = itemView.findViewById(R.id.ivImage1)
        private val ivImage2: ImageView = itemView.findViewById(R.id.ivImage2)
        private val ivImage3: ImageView = itemView.findViewById(R.id.ivImage3)
        private val tvMoreCount: TextView = itemView.findViewById(R.id.tvMoreCount)

        override fun bindMedia(ad: Ad) {
            val images = ad.adImages
            val imageViews = listOf(ivImage1, ivImage2, ivImage3)
            for (i in imageViews.indices) {
                if (i < images.size) {
                    Glide.with(imageViews[i].context)
                        .load(images[i])
                        .priority(Priority.IMMEDIATE)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.placeholder_image)
                        .error(R.drawable.placeholder_image)
                        .into(imageViews[i])
                }
            }
            // 超出 3 张时显示 "+N"
            if (images.size > 3) {
                tvMoreCount.visibility = View.VISIBLE
                tvMoreCount.text = "+${images.size - 3}"
            } else {
                tvMoreCount.visibility = View.GONE
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Ad>() {
        override fun areItemsTheSame(oldItem: Ad, newItem: Ad): Boolean =
            oldItem.adId == newItem.adId

        override fun areContentsTheSame(oldItem: Ad, newItem: Ad): Boolean =
            oldItem == newItem
    }
}
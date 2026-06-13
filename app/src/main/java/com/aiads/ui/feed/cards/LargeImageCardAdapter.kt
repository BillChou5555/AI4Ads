package com.aiads.ui.feed.cards

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aiads.R
import com.aiads.data.local.DatabaseHelper
import com.aiads.data.model.Ad
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
/**
 * 大图卡片 Adapter。
 *
 * ListAdapter<Ad, LargeImageCardViewHolder> 中：
 * - 第一个泛型 Ad 是数据项类型
 * - 第二个泛型 LargeImageCardViewHolder 是 ViewHolder 类型
 */
class LargeImageCardAdapter(
    private val onCardClick: (Ad) -> Unit,
    private val onTagClick: (String) -> Unit,
    private val onLikeClick: (Ad) -> Unit,
    private val onBookmarkClick: (Ad) -> Unit,
    private val onShareClick: (Ad) -> Unit
) : ListAdapter<Ad, LargeImageCardAdapter.ViewHolder>(DiffCallback) {
    var interactionMap: Map<String, DatabaseHelper.InteractionState> = emptyMap()
    var activeFilterTags: Set<String> = emptySet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card_large_image, parent, false)
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
        private val ivMedia: ImageView = itemView.findViewById(R.id.ivMedia)

        override fun bindMedia(ad: Ad) {
            if (ad.adImages.isNotEmpty()){
                Glide.with(ivMedia.context)
                    .load(ad.adImages[0])
                    .priority(Priority.IMMEDIATE)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .into(ivMedia)
            }
        }
    }

    /** DiffUtil 回调：告诉 ListAdapter 如何判断两个 item 是否是同一条数据 */
    companion object DiffCallback : DiffUtil.ItemCallback<Ad>() {
        override fun areItemsTheSame(oldItem: Ad, newItem: Ad): Boolean =
            oldItem.adId == newItem.adId

        override fun areContentsTheSame(oldItem: Ad, newItem: Ad): Boolean =
            oldItem == newItem  // data class 的 equals 会比较所有字段
    }
}
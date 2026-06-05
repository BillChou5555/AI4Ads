package com.aiads.ui.feed.cards

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aiads.R
import com.aiads.data.local.DatabaseHelper
import com.aiads.data.model.Ad
import com.aiads.data.model.AdTag
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * 卡片 ViewHolder 基类，处理所有卡片共有的信息区和操作区。
 *
 * ## 为什么用抽象类？
 * 4 种卡片的媒体区域各不相同（图片、多图、视频、无），但标题/摘要/标签/操作按钮
 * 的绑定逻辑完全一样。基类封装公共逻辑，子类只需实现 bindMedia() 处理媒体区域。
 *
 * ## findViewById 能跨 <include> 查找吗？
 * 可以。Android 的 findViewById 在 itemView 的整个子树中递归查找，不论控件
 * 在 <include> 内部还是外层。这就是我们可以把公共 ID 放在 item_card_common_info.xml
 * 中，但仍然在 BaseCardViewHolder 中通过 findViewById 找到它们的原因。
 */
abstract class BaseCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    // 公共控件——所有卡片都有
    protected val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
    protected val tvAiSummary: TextView = itemView.findViewById(R.id.tvAiSummary)
    protected val chipGroup: ChipGroup = itemView.findViewById(R.id.chipGroupTags)
    protected val btnLike: ImageButton = itemView.findViewById(R.id.btnLike)
    protected val btnBookmark: ImageButton = itemView.findViewById(R.id.btnBookmark)
    protected val btnShare: ImageButton = itemView.findViewById(R.id.btnShare)

    /**
     * 绑定数据到 ViewHolder。这是模板方法模式的体现：
     * 1. 先绑定公共区域（本方法）
     * 2. 再绑定媒体区域（子类实现的 bindMedia）
     *
     * @param ad          广告数据
     * @param interaction 用户交互状态（点赞/收藏），可能为 null
     * @param onCardClick 卡片点击回调
     * @param onTagClick  标签点击回调
     */
    fun bind(
        ad: Ad,
        interaction: DatabaseHelper.InteractionState?,
        onCardClick: (Ad) -> Unit,
        onTagClick: (String) -> Unit,
        onLikeClick: (Ad) -> Unit,
        onBookmarkClick: (Ad) -> Unit,
        onShareClick: (Ad) -> Unit
    ) {
        // 标题
        tvTitle.text = ad.title

        // AI 摘要
        if (ad.aiSummary.isNotEmpty()) {
            tvAiSummary.visibility = View.VISIBLE
            tvAiSummary.text = ad.aiSummary
        } else {
            tvAiSummary.visibility = View.GONE
        }

        // 标签 Chips
        bindTags(ad.aiTags, onTagClick)

        // 操作按钮状态
        btnLike.isSelected = interaction?.isLiked == true
        btnBookmark.isSelected = interaction?.isBookmarked == true
        btnShare.isSelected = interaction?.isShared == true

        // 操作按钮点击——setOnClickListener 会覆盖之前的监听器
        btnLike.setOnClickListener { onLikeClick(ad) }
        btnBookmark.setOnClickListener { onBookmarkClick(ad) }
        btnShare.setOnClickListener { onShareClick(ad) }

        // 卡片根布局点击 → 跳转详情
        itemView.setOnClickListener { onCardClick(ad) }

        // 交给子类处理媒体区域
        bindMedia(ad)
    }

    /** 子类实现：绑定各自不同的媒体区域 */
    protected abstract fun bindMedia(ad: Ad)

    /**
     * 动态创建 Chip 标签。
     *
     * Chip 只在有数据时创建——空标签列表时 ChipGroup 为空，不占空间。
     * M3 使用默认样式的 Chip，M7 会替换为自定义颜色对应不同品类。
     */
    private fun bindTags(tags: List<AdTag>, onTagClick: (String) -> Unit) {
        chipGroup.removeAllViews()
        for (tag in tags) {
            val chip = Chip(chipGroup.context).apply {
                text = "[${tag.category}] ${tag.value}"
                isCheckable = false
                setOnClickListener { onTagClick(tag.value) }
            }
            chipGroup.addView(chip)
        }
    }
}
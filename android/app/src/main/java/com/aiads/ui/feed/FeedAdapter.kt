package com.aiads.ui.feed

import androidx.recyclerview.widget.ConcatAdapter
import com.aiads.data.local.DatabaseHelper
import com.aiads.data.model.Ad
import com.aiads.ui.feed.cards.LargeImageCardAdapter
import com.aiads.ui.feed.cards.MultiImageCardAdapter
import com.aiads.ui.feed.cards.TextOnlyCardAdapter
import com.aiads.ui.feed.cards.VideoCardAdapter

enum class CardType {
    VIDEO, MULTI_IMAGE, LARGE_IMAGE, TEXT_ONLY
}

fun detectCardType(ad: Ad): CardType {
    return when {
        !ad.adVideoUrl.isNullOrEmpty() -> CardType.VIDEO
        ad.adImages.size >= 2          -> CardType.MULTI_IMAGE
        ad.adImages.size == 1          -> CardType.LARGE_IMAGE
        else                           -> CardType.TEXT_ONLY
    }
}

class FeedAdapter(
    private val onCardClick: (Ad) -> Unit,
    private val onTagClick: (String) -> Unit,
    private val onLikeClick: (Ad) -> Unit,
    private val onBookmarkClick: (Ad) -> Unit,
    private val onShareClick: (Ad) -> Unit
) {
    val videoAdapter = VideoCardAdapter(onCardClick, onTagClick, onLikeClick, onBookmarkClick, onShareClick)
    val largeImageAdapter = LargeImageCardAdapter(onCardClick, onTagClick, onLikeClick, onBookmarkClick,
        onShareClick)
    val multiImageAdapter = MultiImageCardAdapter(onCardClick, onTagClick, onLikeClick, onBookmarkClick,
        onShareClick)
    val textOnlyAdapter = TextOnlyCardAdapter(onCardClick, onTagClick, onLikeClick, onBookmarkClick,
        onShareClick)

    val concatAdapter = ConcatAdapter(
        videoAdapter, largeImageAdapter, multiImageAdapter, textOnlyAdapter
    )
    var activeFilterTags: Set<String> = emptySet()

    fun updateFilterTags(tags: Set<String>) {
        videoAdapter.activeFilterTags = tags
        largeImageAdapter.activeFilterTags = tags
        multiImageAdapter.activeFilterTags = tags
        textOnlyAdapter.activeFilterTags = tags
        concatAdapter.notifyDataSetChanged()
    }

    /**
     * 提交新数据列表。
     *
     * @param ads            广告数据
     * @param interactionMap 当前交互状态映射，每次 submit 时注入，确保子 Adapter 拿到的是最新数据
     */
    fun submitList(ads: List<Ad>, interactionMap: Map<String, DatabaseHelper.InteractionState>) {
        videoAdapter.activeFilterTags = activeFilterTags
        largeImageAdapter.activeFilterTags = activeFilterTags
        multiImageAdapter.activeFilterTags = activeFilterTags
        textOnlyAdapter.activeFilterTags = activeFilterTags

        videoAdapter.interactionMap = interactionMap
        largeImageAdapter.interactionMap = interactionMap
        multiImageAdapter.interactionMap = interactionMap
        textOnlyAdapter.interactionMap = interactionMap

        videoAdapter.submitList(ads.filter { detectCardType(it) == CardType.VIDEO })
        largeImageAdapter.submitList(ads.filter { detectCardType(it) == CardType.LARGE_IMAGE })
        multiImageAdapter.submitList(ads.filter { detectCardType(it) == CardType.MULTI_IMAGE })
        textOnlyAdapter.submitList(ads.filter { detectCardType(it) == CardType.TEXT_ONLY })
    }

    fun updateInteraction(adId: String, newState: DatabaseHelper.InteractionState) {
        videoAdapter.interactionMap = videoAdapter.interactionMap + (adId to newState)
        val posV = videoAdapter.currentList.indexOfFirst { it.adId == adId }
        if (posV >= 0) videoAdapter.notifyItemChanged(posV)

        largeImageAdapter.interactionMap = largeImageAdapter.interactionMap + (adId to newState)
        val posL = largeImageAdapter.currentList.indexOfFirst { it.adId == adId }
        if (posL >= 0) largeImageAdapter.notifyItemChanged(posL)

        multiImageAdapter.interactionMap = multiImageAdapter.interactionMap + (adId to newState)
        val posM = multiImageAdapter.currentList.indexOfFirst { it.adId == adId }
        if (posM >= 0) multiImageAdapter.notifyItemChanged(posM)

        textOnlyAdapter.interactionMap = textOnlyAdapter.interactionMap + (adId to newState)
        val posT = textOnlyAdapter.currentList.indexOfFirst { it.adId == adId }
        if (posT >= 0) textOnlyAdapter.notifyItemChanged(posT)
    }
}
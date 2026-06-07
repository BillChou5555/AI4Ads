package com.aiads.ui.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.aiads.data.model.Ad

sealed class MediaItem {
    data class Image(val url: String) : MediaItem()
    data class Video(val url: String) : MediaItem()
}

fun buildMediaList(ad: Ad): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    ad.adImages.forEach { items.add(MediaItem.Image(it)) }
    ad.adVideoUrl?.let { items.add(MediaItem.Video(it)) }
    return items
}

class DetailViewModel(
    private val adCache: Map<String, Ad>,
    private val adId: String
) : ViewModel() {

    private val _ad = MutableLiveData<Ad>()
    val ad: LiveData<Ad> = _ad

    /** 媒体列表：Ad.adImages + Ad.adVideoUrl 合并为一个列表 */
    val mediaItems: List<MediaItem>
        get() = _ad.value?.let { buildMediaList(it) } ?: emptyList()

    init {
        _ad.value = adCache[adId]
    }
}
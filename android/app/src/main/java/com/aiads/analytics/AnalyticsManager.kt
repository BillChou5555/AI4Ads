package com.aiads.analytics

import com.aiads.data.local.DatabaseHelper
import com.aiads.data.model.Ad
import com.aiads.util.DeviceIdProvider

class AnalyticsManager(
    private val database: DatabaseHelper,
    private val deviceIdProvider: DeviceIdProvider
) {
    /** 曝光去重：已上报过曝光的 adId 集合 */
    private val exposedAdIds = mutableSetOf<String>()

    // ========== 公开的埋点方法 ==========

    fun trackImpression(ad: Ad, tab: String) {
        if (ad.adId in exposedAdIds) return
        exposedAdIds.add(ad.adId)
        insert(ad.adId, "impression", tab = tab)
    }

    fun trackClick(ad: Ad, position: String) {
        insert(ad.adId, "click", position = position)
    }

    fun trackLike(ad: Ad) {
        insert(ad.adId, "like")
    }

    fun trackFavorite(ad: Ad) {
        insert(ad.adId, "favorite")
    }

    fun trackShare(ad: Ad) {
        insert(ad.adId, "share")
    }

    fun trackTagFilter(tag: String, ad: Ad) {
        insert(ad.adId, "tag_filter", tagName = tag)
    }

    // ========== 查询方法（供看板使用） ==========

    fun getEventCountsByType(): Map<String, Int> = database.getEventCountsByType()
    fun getAllEvents(): List<DatabaseHelper.AnalyticsEventRecord> = database.getAllEvents()

    // ========== 内部工具 ==========

    private fun insert(
        adId: String, eventType: String,
        tab: String? = null, position: String? = null, tagName: String? = null
    ) {
        database.insertAnalyticsEvent(
            deviceId = deviceIdProvider.deviceId,
            adId = adId,
            eventType = eventType,
            eventTime = System.currentTimeMillis(),
            tab = tab,
            position = position,
            tagName = tagName
        )
    }
}
package com.aiads.data.repository

import com.aiads.data.model.Ad
import com.aiads.data.model.FeedResponse
import com.aiads.data.remote.AdApi

/**
 * Feed 数据仓库，封装广告流数据的获取逻辑。
 *
 * ## 为什么需要 Repository 层？
 * ViewModel 不应该知道数据来自网络还是本地缓存。Repository 作为中间层，
 * 对外暴露简单的 suspend 函数，对内决定从哪取数据。现在只有网络，未来
 * M5/M6 会加入本地缓存——到时候只改 Repository，ViewModel 不用动。
 *
 * ## 为什么 getFeed 不直接返回 List<Ad>？
 * 因为调用方需要 cursor（下一页游标）和 hasMore（是否还有更多），
 * 这些是分页控制信息，不是广告数据本身。返回 FeedResponse 完整保留。
 */
class FeedRepository(private val adApi: AdApi) {

    /**
     * 获取指定 Tab 的一页 Feed 数据。
     *
     * @param tab    频道标识（featured / ecommerce / local）
     * @param size   每页数量，默认 20
     * @param cursor 分页游标，首页传 null
     */
    suspend fun getFeed(
        tab: String,
        size: Int = 20,
        cursor: String? = null
    ): FeedResponse {
        return adApi.getFeed(tab = tab, size = size, cursor = cursor)
    }
}
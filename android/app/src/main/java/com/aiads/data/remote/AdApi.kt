package com.aiads.data.remote

import com.aiads.data.model.FeedResponse
import com.aiads.data.model.LikeResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 广告相关 API。
 * Retrofit 在运行时会为这个接口自动生成实现类，每个方法对应一个 HTTP 请求。
 */
interface AdApi {

    /**
     * Feed 流分发。
     * @param tab 频道标识（featured/ecommerce/local）
     * @param size 每页数量
     * @param cursor 分页游标，首页传 null
     */
    @GET("api/v1/ads/feed")
    suspend fun getFeed(
        @Query("tab") tab: String,
        @Query("size") size: Int = 20,
        @Query("cursor") cursor: String? = null
    ): FeedResponse

    /**
     * 点赞/取消赞。
     * 服务端根据当前状态自动切换——已点赞则取消，未点赞则点赞。
     */
    @POST("api/v1/ads/{ad_id}/like")
    suspend fun likeAd(
        @Path("ad_id") adId: String
    ): LikeResponse
}

package com.aiads.data.model

import com.google.gson.annotations.SerializedName

/**
 * 广告数据模型，对应服务端 /api/v1/ads/feed 返回的每条广告。
 * @SerializedName 将 JSON 的 snake_case 字段映射到 Kotlin 的 camelCase 属性。
 */
data class Ad(
    @SerializedName("id") val adId: String,
    @SerializedName("title") val title: String,
    @SerializedName("provider") val provider: String,
    @SerializedName("ad_text") val adText: String,
    @SerializedName("ad_images") val adImages: List<String> = emptyList(),
    @SerializedName("ad_video_audio") val adVideoUrl: String? = null,
    @SerializedName("ai_summary") val aiSummary: String = "",
    @SerializedName("ai_tags") val aiTags: List<AdTag> = emptyList(),
    @SerializedName("tab") val tab: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

/**
 * 智能标签，包含分类和值。
 * 例如：{"category": "品类", "value": "运动鞋"}
 */
data class AdTag(
    @SerializedName("category") val category: String,
    @SerializedName("value") val value: String
)

/**
 * Feed 流接口响应体。
 * cursor 用于下一页请求，hasMore 表示是否还有更多数据。
 */
data class FeedResponse(
    @SerializedName("items") val items: List<Ad>,
    @SerializedName("next_cursor") val cursor: String?
)

/**
 * 搜索接口响应体。
 */
data class SearchResponse(
    @SerializedName("items") val items: List<Ad>
)

/**
 * 点赞/取消赞接口响应体。
 */
data class LikeResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("liked") val liked: Boolean
)

/**
 * 埋点事件，对应服务端 /api/v1/analytics/event 请求体中的一个事件。
 */
data class AnalyticsEvent(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("ad_id") val adId: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("event_time") val eventTime: Long,
    @SerializedName("tab") val tab: String? = null,
    @SerializedName("position") val position: String? = null,
    @SerializedName("tag_name") val tagName: String? = null
)

/**
 * 埋点批量上报请求体。
 */
data class AnalyticsRequest(
    @SerializedName("events") val events: List<AnalyticsEvent>
)

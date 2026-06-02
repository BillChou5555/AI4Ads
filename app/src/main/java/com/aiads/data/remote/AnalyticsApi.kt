package com.aiads.data.remote

import com.aiads.data.model.AnalyticsRequest
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 埋点上报 API。
 */
interface AnalyticsApi {

    /**
     * 批量上报埋点事件。
     */
    @POST("api/v1/analytics/event")
    suspend fun reportEvents(
        @Body request: AnalyticsRequest
    )
}

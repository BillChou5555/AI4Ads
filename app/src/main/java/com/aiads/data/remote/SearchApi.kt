package com.aiads.data.remote

import com.aiads.data.model.SearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 搜索相关 API。
 */
interface SearchApi {

    /**
     * RAG 对话式搜索。
     * @param q 用户输入的自然语言查询
     */
    @GET("api/v1/ads/search")
    suspend fun search(
        @Query("q") query: String
    ): SearchResponse
}

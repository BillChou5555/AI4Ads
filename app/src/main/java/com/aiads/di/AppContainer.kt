package com.aiads.di

import android.content.Context
import com.aiads.BuildConfig
import com.aiads.data.local.DatabaseHelper
import com.aiads.data.remote.AdApi
import com.aiads.data.remote.AnalyticsApi
import com.aiads.data.remote.DeviceIdInterceptor
import com.aiads.data.remote.SearchApi
import com.aiads.util.DeviceIdProvider
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 手动依赖注入容器。
 *
 * 全局对象工厂，Application 创建并持有它。
 * `by lazy` 表示对象在第一次使用时才创建，不是启动时全部创建，节省启动时间。
 *
 * 依赖关系链：
 * DeviceIdProvider → DeviceIdInterceptor → OkHttpClient → Retrofit → AdApi/SearchApi/AnalyticsApi
 * Context → DatabaseHelper
 */
class AppContainer(private val context: Context) {

    // ==================== 基础依赖 ====================

    val deviceIdProvider: DeviceIdProvider by lazy {
        DeviceIdProvider(context)
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(DeviceIdInterceptor(deviceIdProvider))
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ==================== API 接口 ====================

    val adApi: AdApi by lazy { retrofit.create(AdApi::class.java) }
    val searchApi: SearchApi by lazy { retrofit.create(SearchApi::class.java) }
    val analyticsApi: AnalyticsApi by lazy { retrofit.create(AnalyticsApi::class.java) }

    // ==================== 本地数据库 ====================

    val database: DatabaseHelper by lazy { DatabaseHelper(context) }
}

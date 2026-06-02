package com.aiads.data.remote

import com.aiads.util.DeviceIdProvider
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp 拦截器：自动为所有请求添加 X-Device-ID 头。
 *
 * OkHttp 拦截器链：每个请求发出前依次经过所有拦截器，
 * 拦截器可以修改请求、添加头、记录日志等。
 */
class DeviceIdInterceptor(
    private val deviceIdProvider: DeviceIdProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("X-Device-ID", deviceIdProvider.deviceId)
            .build()
        return chain.proceed(newRequest)
    }
}

package com.aiads.util

import android.content.Context
import java.util.UUID

/**
 * 设备 ID 提供者。
 * 首次启动生成一个 UUID，存入 SharedPreferences，之后每次读取同一个值。
 */
class DeviceIdProvider(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (existing != null) return existing

            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
            return newId
        }

    companion object {
        private const val PREFS_NAME = "ai4ads_prefs"
        private const val KEY_DEVICE_ID = "device_id"
    }
}

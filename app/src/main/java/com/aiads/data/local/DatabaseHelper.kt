package com.aiads.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 原生 SQLite 数据库助手。
 *
 * 为什么用 SQLiteOpenHelper 而不是 Room？
 * Room 依赖注解处理器（KAPT/KSP），当前 JDK 25 存在兼容性问题。
 * SQLiteOpenHelper 是 Android SDK 内置的，无需任何注解处理，稳定可靠。
 *
 * 两张表：
 * 1. user_interactions — 点赞、收藏、转发状态
 * 2. search_history — 搜索历史
 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // ==================== 生命周期 ====================

    override fun onCreate(db: SQLiteDatabase) {
        // 创建用户交互状态表
        db.execSQL(
            """
            CREATE TABLE user_interactions (
                ad_id TEXT PRIMARY KEY,
                is_liked INTEGER NOT NULL DEFAULT 0,
                is_bookmarked INTEGER NOT NULL DEFAULT 0,
                is_shared INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        // 创建搜索历史表
        db.execSQL(
            """
            CREATE TABLE search_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                query TEXT NOT NULL UNIQUE,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // M1 阶段版本 1，暂无升级逻辑
    }

    // ==================== 用户交互操作 ====================

    /** 查询某条广告的交互状态，可能为 null */
    fun getInteraction(adId: String): InteractionState? {
        val cursor = readableDatabase.rawQuery(
            "SELECT is_liked, is_bookmarked, is_shared FROM user_interactions WHERE ad_id = ?",
            arrayOf(adId)
        )
        return cursor.use {
            if (it.moveToFirst()) {
                InteractionState(
                    isLiked = it.getInt(0) == 1,
                    isBookmarked = it.getInt(1) == 1,
                    isShared = it.getInt(2) == 1
                )
            } else null
        }
    }

    /** 更新或插入交互状态（主键冲突时替换） */
    fun upsertInteraction(
        adId: String,
        isLiked: Boolean? = null,
        isBookmarked: Boolean? = null,
        isShared: Boolean? = null
    ) {
        val existing = getInteraction(adId)
        val values = ContentValues().apply {
            put("ad_id", adId)
            put("is_liked", isLiked ?: existing?.isLiked ?: false)
            put("is_bookmarked", isBookmarked ?: existing?.isBookmarked ?: false)
            put("is_shared", isShared ?: existing?.isShared ?: false)
        }
        writableDatabase.insertWithOnConflict(
            "user_interactions", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /** 获取所有已点赞的广告 ID */
    fun getLikedAdIds(): List<String> {
        val cursor = readableDatabase.rawQuery(
            "SELECT ad_id FROM user_interactions WHERE is_liked = 1", null
        )
        return cursor.use {
            val ids = mutableListOf<String>()
            while (it.moveToNext()) ids.add(it.getString(0))
            ids
        }
    }

    /** 获取所有已收藏的广告 ID */
    fun getBookmarkedAdIds(): List<String> {
        val cursor = readableDatabase.rawQuery(
            "SELECT ad_id FROM user_interactions WHERE is_bookmarked = 1", null
        )
        return cursor.use {
            val ids = mutableListOf<String>()
            while (it.moveToNext()) ids.add(it.getString(0))
            ids
        }
    }

    // ==================== 搜索历史操作 ====================

    /** 获取最近 10 条搜索历史，时间倒序 */
    fun getSearchHistory(): List<String> {
        val cursor = readableDatabase.rawQuery(
            "SELECT query FROM search_history ORDER BY timestamp DESC LIMIT 10", null
        )
        return cursor.use {
            val queries = mutableListOf<String>()
            while (it.moveToNext()) queries.add(it.getString(0))
            queries
        }
    }

    /** 插入或更新时间戳（UNIQUE 约束冲突时更新时间戳） */
    fun insertSearchHistory(query: String) {
        val values = ContentValues().apply {
            put("query", query)
            put("timestamp", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            "search_history", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /** 清空搜索历史 */
    fun clearSearchHistory() {
        writableDatabase.delete("search_history", null, null)
    }

    // ==================== 数据类与常量 ====================

    /** 用户交互状态值对象 */
    data class InteractionState(
        val isLiked: Boolean = false,
        val isBookmarked: Boolean = false,
        val isShared: Boolean = false
    )

    companion object {
        private const val DATABASE_NAME = "ai4ads.db"
        private const val DATABASE_VERSION = 1
    }
}

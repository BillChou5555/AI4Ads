package com.aiads

import android.app.Application
import com.aiads.di.AppContainer

/**
 * Application 入口类。
 * 在 onCreate 中初始化全局依赖容器 AppContainer。
 */
class App : Application() {

    /** 全局依赖容器，在整个 App 生命周期内唯一 */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

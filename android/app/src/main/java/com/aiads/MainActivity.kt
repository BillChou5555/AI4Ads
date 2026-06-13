package com.aiads

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 单 Activity 宿主。
 * 所有页面（Feed、详情、搜索）都以 Fragment 形式在 Navigation Component 中切换。
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}

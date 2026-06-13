package com.aiads.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aiads.R

/**
 * 搜索页 — M13 将完整实现。
 * 当前为占位，确保 M2 的导航图能编译通过。
 */
class SearchFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_feed_tab, container, false)
    }
}

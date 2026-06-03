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
        // 暂用 feed 布局占位，M13 替换为 fragment_search.xml
        return inflater.inflate(R.layout.fragment_feed_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<android.widget.TextView>(R.id.tvTabPlaceholder)?.text =
            "搜索页\n\nM13 将在此实现搜索功能"
    }
}

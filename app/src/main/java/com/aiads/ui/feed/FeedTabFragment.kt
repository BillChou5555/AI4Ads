package com.aiads.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aiads.R

/**
 * 单个 Tab 页面的内容 Fragment，作为 ViewPager2 的子页面。
 *
 * 它与 FeedFragment 是不同的类：
 * - FeedFragment  = 宿主，持有 TabLayout + ViewPager2
 * - FeedTabFragment = 子页面，只负责渲染一个 Tab 的广告列表
 *
 * 为什么要分开？ViewPager2 的每个页面只需要内容区域，如果再嵌套一套 TabLayout 会形成递归。
 */
class FeedTabFragment : Fragment() {

    companion object {
        private const val ARG_TAB = "tab"

        /** 创建指定 Tab 的 Fragment 实例 */
        fun newInstance(tab: String): FeedTabFragment {
            return FeedTabFragment().apply {
                arguments = Bundle().apply { putString(ARG_TAB, tab) }
            }
        }
    }

    /** 当前 Tab 标识（featured / ecommerce / local） */
    private val tab: String by lazy {
        arguments?.getString(ARG_TAB) ?: "featured"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_feed_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // M2 阶段：显示当前 Tab 名称作为占位
        // M3 将替换为 RecyclerView + FeedViewModel
        val labelMap = mapOf("featured" to "精选", "ecommerce" to "电商", "local" to "本地")
        val label = labelMap[tab] ?: tab
        view.findViewById<android.widget.TextView>(R.id.tvTabPlaceholder)?.text =
            "$label 频道\n\nM3 将在此渲染广告列表"
    }
}

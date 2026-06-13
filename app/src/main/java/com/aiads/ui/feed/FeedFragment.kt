package com.aiads.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.aiads.R
import com.aiads.databinding.FragmentFeedBinding
import com.google.android.material.tabs.TabLayoutMediator

/**
 * 信息流主页面 — M2 实现顶部 Tab 导航。
 *
 * ## 架构角色
 * 这是整个 App 的"首页"。它包含：
 * - 顶部 TabLayout（精选/电商/本地 3 个频道）
 * - 搜索按钮（跳转搜索页）
 * - ViewPager2（承载 3 个 FeedTabFragment）
 *
 * ## 与 FeedTabFragment 的关系
 * ```
 * FeedFragment (宿主,由 NavHostFragment 管理)
 *   ├── TabLayout      ← 3 个 Tab 标签
 *   ├── ImageButton    ← 搜索入口
 *   └── ViewPager2     ← 页面滑动容器
 *         ├── FeedTabFragment(tab="featured")   ← 精选频道
 *         ├── FeedTabFragment(tab="ecommerce")  ← 电商频道
 *         └── FeedTabFragment(tab="local")      ← 本地频道
 * ```
 *
 * ## 为什么 TabLayout 放在 FeedFragment 而非 MainActivity？
 * 当用户点击搜索或进入详情页时，Navigation Component 会将 FeedFragment
 * 替换为 SearchFragment/DetailFragment。如果 TabLayout 放在 Activity 层，
 * 它在搜索/详情页仍然可见——这是错误的。放在 FeedFragment 中意味着
 * Tab 栏随 Feed 页面自然出现/消失。
 *
 * ## ViewPager2 + FragmentStateAdapter 如何协同？
 * 1. FragmentStateAdapter 为每个 Tab 创建一个 FeedTabFragment
 * 2. ViewPager2 管理这些 Fragment 的显示与滑动
 * 3. TabLayoutMediator 监听 ViewPager2 的页面变化 → 更新 Tab 高亮
 *    同时也监听 Tab 的点击 → 通知 ViewPager2 翻到对应页
 * 4. offscreenPageLimit=1 表示预加载相邻 1 个 Tab 的 Fragment
 */
class FeedFragment : Fragment() {

    // ViewBinding：通过 binding 对象安全访问布局中的控件
    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
        binding.btnDashboard.setOnClickListener {
            findNavController().navigate(R.id.action_to_dashboard)
        }
        setupSearchButton()
    }

    /**
     * 初始化 ViewPager2 + TabLayout 联动。
     *
     * FragmentStateAdapter 接收 this（FeedFragment），
     * 内部会自动使用 childFragmentManager 来管理子 Fragment。
     * 这意味着 ViewPager2 内的 FeedTabFragment 的生命周期
     * 跟随 FeedFragment，而非 MainActivity。
     */
    private fun setupViewPager() {
        // 1. 创建 Adapter：定义 3 个 Tab 及其对应的 Fragment
        val adapter = FeedPagerAdapter(this)

        // 2. 绑定 Adapter 到 ViewPager2
        binding.viewPager.adapter = adapter

        // 3. 预加载相邻 Tab（当前 Tab 在中间时，左右两个 Tab 的 Fragment 已创建好）
        binding.viewPager.offscreenPageLimit = 1

        // 4. 用 TabLayoutMediator 桥接 TabLayout 和 ViewPager2
        //    它做的事：
        //    - 为每个 Tab 创建标签，文字来自 TabInfo.label
        //    - Tab 被点击 → ViewPager2.setCurrentItem()
        //    - ViewPager2 滑动 → Tab 指示器跟随移动
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = FeedPagerAdapter.TABS[position].label
        }.attach()
    }

    /** 搜索按钮点击 → 跳转到 SearchFragment */
    private fun setupSearchButton() {
        // 导航到搜索页
        binding.btnSearch.setOnClickListener {
            findNavController().navigate(R.id.action_to_search)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null  // 防止内存泄漏：Fragment 销毁时清空 binding 引用
    }
}

// =============================================================================
// FeedPagerAdapter — ViewPager2 的页面适配器
// =============================================================================

/**
 * 为 ViewPager2 的每个页面创建对应的 FeedTabFragment。
 *
 * ## 为什么用 FragmentStateAdapter？
 * ViewPager2 有两种 Adapter：
 * - RecyclerView.Adapter：用于普通 View
 * - FragmentStateAdapter：用于 Fragment 页面
 * 我们需要每个 Tab 是一个独立的 Fragment（未来各自持有 ViewModel），所以用后者。
 *
 * ## FragmentStateAdapter 的构造参数
 * 接收一个 Fragment，内部调用 fragment.childFragmentManager。
 * 这意味着 ViewPager2 管理的子 Fragment 使用 childFragmentManager，
 * 它们的生命周期绑定到 FeedFragment 而非 MainActivity。
 *
 * ## createFragment 的调用时机
 * FragmentStateAdapter 只在需要显示某个页面时才调用 createFragment(position)。
 * 配合 offscreenPageLimit=1，首次打开 App 时会创建 position 0 和 1 的 Fragment。
 */
class FeedPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    companion object {
        /** Tab 配置列表：服务端标识 + 显示名称 */
        val TABS = listOf(
            TabInfo("featured", "精选"),
            TabInfo("ecommerce", "电商"),
            TabInfo("local", "本地")
        )
    }

    override fun getItemCount(): Int = TABS.size

    override fun createFragment(position: Int): Fragment {
        return FeedTabFragment.newInstance(TABS[position].key)
    }
}

/**
 * 单个 Tab 的配置信息。
 * @param key 服务端频道标识，传给 API 的 tab 参数
 * @param label 在 TabLayout 上显示的文字
 */
data class TabInfo(val key: String, val label: String)

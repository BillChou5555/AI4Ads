package com.aiads.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.aiads.App
import com.aiads.R
import com.aiads.data.model.Ad
import com.aiads.data.repository.FeedRepository
import com.aiads.data.repository.InteractionRepository
import com.aiads.analytics.AnalyticsManager
import com.aiads.di.AppContainer
import com.aiads.player.PlaybackManager
import com.aiads.player.PlayerPool
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.cardview.widget.CardView
import com.aiads.data.local.DatabaseHelper
import com.google.android.material.chip.Chip


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
    private val appContainer: AppContainer by lazy { (requireActivity().application as com.aiads.App).container }
    private val playerPool: PlayerPool by lazy { appContainer.playerPool }
    private lateinit var playbackManager: PlaybackManager
    private val repository: FeedRepository by lazy {appContainer.feedRepository}
    private val interactionRepository: InteractionRepository by lazy {
        appContainer.interactionRepository }
    private val analyticsManager: AnalyticsManager by lazy { appContainer.analyticsManager }
    private var interactionMap: Map<String, DatabaseHelper.InteractionState> = emptyMap()

    /**
     * 自定义 ViewModelFactory。
     *
     * ## 为什么需要 Factory？
     * Android 默认的 ViewModelProvider 用无参构造函数创建 ViewModel。
     * 但 FeedViewModel 需要 repository 和 tab 两个参数。
     * Factory 的作用就是告诉 ViewModelProvider "怎么创建这个 ViewModel"。
     *
     * ## FeedTabFragment 为什么能拿到 AppContainer？
     * App 类持有 AppContainer 单例，通过 (requireActivity().application as App).container 获取。
     * Fragment → Activity → Application → AppContainer → FeedRepository，
     * 这就是手动 DI 的依赖链。
     */
    private val viewModel: FeedViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FeedViewModel(repository, tab) as T
            }
        }
    }

    private lateinit var feedAdapter: FeedAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_feed_tab, container, false)
    }

    override fun onResume() {
        super.onResume()
        if (::playbackManager.isInitialized) {
            playbackManager.attach()    // 重新注册滚动监听
            playbackManager.evaluate()  // 立即评估播放
        }
    }

    override fun onPause() {
        super.onPause()
        if (::playbackManager.isInitialized) {
            playbackManager.detach()
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        if (::playbackManager.isInitialized) {
            playbackManager.destroy()       // 彻底清理
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        val swipeRefresh: SwipeRefreshLayout = view.findViewById(R.id.swipeRefresh)

        setupRecyclerView()
        setupSwipeRefresh(swipeRefresh)
        setupScrollListener()
        observeViewModel(swipeRefresh)
    }

    // ==================== RecyclerView 初始化 ====================

    private fun setupRecyclerView() {
        feedAdapter = FeedAdapter(
            onCardClick = { ad -> navigateToDetail(ad) },
            onTagClick = { tag ->
                val firstAd = viewModel.ads.value?.firstOrNull()
                if (firstAd != null) analyticsManager.trackTagFilter(tag, firstAd)
                viewModel.toggleFilterTag(tag)
            },
            onLikeClick = { ad ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val newState = interactionRepository.toggleLike(ad.adId)
                    interactionMap = interactionMap + (ad.adId to newState)
                    feedAdapter.updateInteraction(ad.adId, newState)
                    analyticsManager.trackLike(ad)
                }
            },
            onBookmarkClick = { ad ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val newState = interactionRepository.toggleBookmark(ad.adId)
                    interactionMap = interactionMap + (ad.adId to newState)
                    feedAdapter.updateInteraction(ad.adId, newState)
                     analyticsManager.trackFavorite(ad)
                }
            },
            onShareClick = { ad ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val newState = interactionRepository.toggleShare(ad.adId)
                    interactionMap = interactionMap + (ad.adId to newState)
                    feedAdapter.updateInteraction(ad.adId, newState)
                    analyticsManager.trackShare(ad)
                }
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = feedAdapter.concatAdapter
        playbackManager = PlaybackManager(playerPool, recyclerView)
        playbackManager.attach()
    }
    // ==================== 下拉刷新 ====================

    private fun setupSwipeRefresh(swipeRefresh: SwipeRefreshLayout) {
        swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }
    // ==================== 上拉加载更多 ====================

    /**
     * 监听 RecyclerView 滚动，距底部 3 项时触发加载更多。
     *
     * ## 为什么是 3？
     * 如果到底部才触发，用户会看到加载指示器然后等待。
     * 提前 3 项触发意味着下一页数据大概率在用户滑到底部之前就回来了。
     */
    private fun setupScrollListener() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return  // 只处理向下滚动
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val total = recyclerView.adapter?.itemCount ?: 0
                if (lastVisible >= total - 3) {
                    viewModel.loadMore()
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                checkExposures()
            }
            }
        })
    }

    private fun checkExposures() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        val ads = viewModel.ads.value ?: return

        for (i in firstVisible..lastVisible) {
            val view = layoutManager.findViewByPosition(i) ?: continue
            val ad = ads.getOrNull(i) ?: continue

            // 计算可见比例
            val visibleTop = if (view.top < 0) 0 else view.top
            val visibleBottom = if (view.bottom > recyclerView.height) recyclerView.height else view.bottom
            val visibleHeight = visibleBottom - visibleTop
            val totalHeight = view.height
            if (totalHeight > 0 && visibleHeight.toFloat() / totalHeight >= 0.5f) {
                analyticsManager.trackImpression(ad, tab)
            }
        }
    }

    // ==================== 观察 ViewModel ====================

    private fun observeViewModel(swipeRefresh: SwipeRefreshLayout) {
        // 数据变化 → 更新列表
        viewModel.ads.observe(viewLifecycleOwner) { ads ->
            viewLifecycleOwner.lifecycleScope.launch {
                val ids = ads.map { it.adId }.toSet()
                interactionMap = interactionRepository.getInteractionMap(ids)
                feedAdapter.submitList(ads, interactionMap)
                recyclerView.post { playbackManager.evaluate() }
            }
            recyclerView.post { playbackManager.evaluate() }
        }

        // 刷新状态 → 控制下拉动画
        viewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            swipeRefresh.isRefreshing = isRefreshing
        }

        // 错误提示
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                android.widget.Toast.makeText(requireContext(), it,
                    android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // 标签选中状态
        val filterBar = requireView().findViewById<androidx.cardview.widget.CardView>(R.id.filterBar)
        val chipGroupFilter = requireView().findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupFilter)
        val tvClearAll = requireView().findViewById<TextView>(R.id.tvClearAll)

        tvClearAll.setOnClickListener { viewModel.clearFilter() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeFilterTags.collect { tags ->
                if (tags.isEmpty()) {
                    filterBar.visibility = View.GONE
                } else {
                    filterBar.visibility = View.VISIBLE
                    chipGroupFilter.removeAllViews()
                    for (tag in tags) {
                        val chip =
                            Chip(chipGroupFilter.context).apply{
                            text = tag
                            isCloseIconVisible = true
                            setOnCloseIconClickListener { viewModel.toggleFilterTag(tag) }
                        }
                        chipGroupFilter.addView(chip)
                    }
                }
                feedAdapter.updateFilterTags(tags)
            }
        }
    }

    // ==================== 导航 ====================

    private fun navigateToDetail(ad: Ad) {
        analyticsManager.trackClick(ad, "card")
        val bundle = Bundle().apply { putString("adId", ad.adId) }
        findNavController().navigate(R.id.action_to_detail, bundle)
    }
}

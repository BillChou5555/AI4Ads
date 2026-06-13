package com.aiads.ui.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiads.App
import com.aiads.R
import com.aiads.analytics.AnalyticsManager
import com.aiads.data.local.DatabaseHelper
import com.aiads.data.model.Ad
import com.aiads.data.remote.SearchApi
import com.aiads.data.repository.InteractionRepository
import com.aiads.di.AppContainer
import com.aiads.player.PlaybackManager
import com.aiads.player.PlayerPool
import com.aiads.ui.feed.FeedAdapter
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    // ===== DI =====
    private val appContainer: AppContainer by lazy {
        (requireActivity().application as App).container
    }
    private val playerPool: PlayerPool by lazy { appContainer.playerPool }
    private val searchApi: SearchApi by lazy { appContainer.searchApi }
    private val interactionRepository: InteractionRepository by lazy { appContainer.interactionRepository }
    private val analyticsManager: AnalyticsManager by lazy { appContainer.analyticsManager }

    // ===== ViewModel =====
    private val viewModel: SearchViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(searchApi, appContainer.database) as T
            }
        }
    }

    // ===== 状态 =====
    private var interactionMap: Map<String, DatabaseHelper.InteractionState> = emptyMap()
    private lateinit var feedAdapter: FeedAdapter
    private lateinit var playbackManager: PlaybackManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val llHistoryContainer = view.findViewById<View>(R.id.llHistoryContainer)
        val tvClearHistory = view.findViewById<TextView>(R.id.tvClearHistory)
        val recyclerHistory = view.findViewById<RecyclerView>(R.id.recyclerHistory)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val recyclerResults = view.findViewById<RecyclerView>(R.id.recyclerResults)
        val swipeRefresh = view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefresh)

        // 返回按钮
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // 搜索历史列表
        recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        val historyAdapter = object : RecyclerView.Adapter<SearchHistoryViewHolder>() {
            private var items: List<String> = emptyList()
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchHistoryViewHolder {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_search_history, parent, false)
                return SearchHistoryViewHolder(v)
            }
            override fun onBindViewHolder(holder: SearchHistoryViewHolder, position: Int) {
                holder.tvQuery.text = items[position]
                holder.itemView.setOnClickListener {
                    etSearch.setText(items[position])
                    doSearch(items[position])
                }
            }
            override fun getItemCount() = items.size
            fun submitList(list: List<String>) { items = list; notifyDataSetChanged() }
        }
        recyclerHistory.adapter = historyAdapter

        // 搜索结果列表（复用 FeedAdapter）
        feedAdapter = FeedAdapter(
            onCardClick = { ad -> navigateToDetail(ad) },
            onTagClick = { /* 搜索结果不做标签过滤 */ },
            onLikeClick = { ad -> toggleLike(ad) },
            onBookmarkClick = { ad -> toggleBookmark(ad) },
            onShareClick = { ad -> toggleShare(ad) }
        )
        recyclerResults.layoutManager = LinearLayoutManager(requireContext()).apply {
            initialPrefetchItemCount = 4
        }
        recyclerResults.adapter = feedAdapter.concatAdapter

        // 视频播放（复用 PlaybackManager）
        playbackManager = PlaybackManager(playerPool, recyclerResults)

        // 搜索框：键盘搜索按钮
        etSearch.requestFocus()
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString().trim()
                if (query.isNotEmpty()) doSearch(query)
                true
            } else false
        }

        // 清空搜索历史
        tvClearHistory.setOnClickListener { viewModel.clearHistory() }

        // 观察搜索历史
        viewModel.history.observe(viewLifecycleOwner) { history ->
            historyAdapter.submitList(history)
        }

        // 观察搜索状态
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                SearchState.Idle -> {
                    llHistoryContainer.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = View.GONE
                    swipeRefresh.visibility = View.VISIBLE
                    playbackManager.detach()
                }
                SearchState.Loading -> {
                    llHistoryContainer.visibility = View.GONE
                    progressBar.visibility = View.VISIBLE
                    tvEmpty.visibility = View.GONE
                    swipeRefresh.visibility = View.VISIBLE
                }
                is SearchState.Success -> {
                    llHistoryContainer.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = View.GONE
                    swipeRefresh.visibility = View.VISIBLE
                    loadInteractionState(state.items)
                    recyclerResults.post { playbackManager.evaluate() }
                }
                is SearchState.Empty -> {
                    llHistoryContainer.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = state.message
                    swipeRefresh.visibility = View.VISIBLE
                }
                is SearchState.Error -> {
                    llHistoryContainer.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = state.message
                    swipeRefresh.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun doSearch(query: String) {
        hideKeyboard()
        viewModel.search(query)
    }

    private fun loadInteractionState(ads: List<Ad>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ids = ads.map { it.adId }.toSet()
            interactionMap = interactionRepository.getInteractionMap(ids)
            feedAdapter.submitList(ads, interactionMap)
        }
    }

    private fun toggleLike(ad: Ad) {
        viewLifecycleOwner.lifecycleScope.launch {
            val newState = interactionRepository.toggleLike(ad.adId)
            interactionMap = interactionMap + (ad.adId to newState)
            feedAdapter.updateInteraction(ad.adId, newState)
            analyticsManager.trackLike(ad)
        }
    }

    private fun toggleBookmark(ad: Ad) {
        viewLifecycleOwner.lifecycleScope.launch {
            val newState = interactionRepository.toggleBookmark(ad.adId)
            interactionMap = interactionMap + (ad.adId to newState)
            feedAdapter.updateInteraction(ad.adId, newState)
            analyticsManager.trackFavorite(ad)
        }
    }

    private fun toggleShare(ad: Ad) {
        viewLifecycleOwner.lifecycleScope.launch {
            val newState = interactionRepository.toggleShare(ad.adId)
            interactionMap = interactionMap + (ad.adId to newState)
            feedAdapter.updateInteraction(ad.adId, newState)
            analyticsManager.trackShare(ad)
        }
    }

    private fun navigateToDetail(ad: Ad) {
        analyticsManager.trackClick(ad, "search")
        val bundle = Bundle().apply { putString("adId", ad.adId) }
        findNavController().navigate(R.id.action_to_detail, bundle)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(requireView().windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        if (::playbackManager.isInitialized) playbackManager.attach()
    }

    override fun onPause() {
        super.onPause()
        if (::playbackManager.isInitialized) playbackManager.detach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::playbackManager.isInitialized) playbackManager.destroy()
    }
}

/** 搜索历史列表 ViewHolder */
class SearchHistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val tvQuery: TextView = view.findViewById(R.id.tvHistoryQuery)
}
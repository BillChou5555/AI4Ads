package com.aiads.ui.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiads.data.model.Ad
import com.aiads.data.repository.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 单个 Tab 频道的 ViewModel，管理该频道的信息流状态。
 *
 * ## 生命周期
 * FeedTabFragment 通过 ViewModelProvider 获取此 ViewModel。
 * ViewModel 的生命周期比 Fragment 长——Fragment 因屏幕旋转重建时，
 * ViewModel 保持不变，数据不会丢失。
 *
 * ## 状态分类
 * 这个 ViewModel 管理两类状态：
 *
 * 1. **数据状态** (LiveData)：UI 观察后渲染
 *    - ads：当前显示的广告列表（经过标签过滤后的结果）
 *    - isLoading：是否正在加载
 *    - isRefreshing：是否正在下拉刷新
 *    - error：错误信息
 *
 * 2. **控制状态** (内部)：
 *    - cursor：下一页游标，null 表示首页
 *    - hasMore：是否还有更多数据
 *    - allAds：全部已加载数据（未过滤），用于过滤标签变化时重新计算
 */
class FeedViewModel(
    private val repository: FeedRepository,
    private val tab: String
) : ViewModel() {

    // ==================== 数据状态（UI 观察） ====================

    private val _ads = MutableLiveData<List<Ad>>(emptyList())
    val ads: LiveData<List<Ad>> = _ads

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    // ==================== 内部状态 ====================

    /** 全部已加载的广告（未过滤），用于过滤标签变化时重新计算展示列表 */
    private val allAds = mutableListOf<Ad>()

    /** 分页游标，null 表示还未加载或已刷新 */
    private var cursor: String? = null

    /** 是否还有更多数据可加载 */
    private var hasMore = true

    // ==================== 标签过滤 ====================

    private val _activeFilterTags = MutableStateFlow<Set<String>>(emptySet())
    val activeFilterTags: StateFlow<Set<String>> = _activeFilterTags

    // ==================== 初始化 ====================

    init {
        loadFirstPage()
        // 监听标签变化，当用户点击 Chip 标签时重新过滤列表
        viewModelScope.launch {
            _activeFilterTags.collect { tags ->
                applyFilter(tags)
            }
        }
    }

    // ==================== 公开方法 ====================

    /** 下拉刷新：清空游标和数据，重新加载首页 */
    fun refresh() {
        cursor = null
        hasMore = true
        loadFirstPage()
    }

    /** 上拉加载更多：追加下一页数据 */
    fun loadMore() {
        if (_isLoading.value == true || !hasMore) return
        loadNextPage()
    }

    /** 切换标签过滤：如果已选中则取消，未选中则添加 */
    fun toggleFilterTag(tag: String) {
        val current = _activeFilterTags.value.toMutableSet()
        if (tag in current) current.remove(tag) else current.add(tag)
        _activeFilterTags.value = current
    }

    /** 清除所有过滤标签，恢复完整列表 */
    fun clearFilter() {
        _activeFilterTags.value = emptySet()
    }

    // ==================== 内部实现 ====================

    /**
     * 加载首页数据。
     * 使用 viewModelScope.launch 发起协程，协程随 ViewModel 销毁自动取消，
     * 不会出现 Fragment 已销毁但网络请求还在进行的内存泄漏问题。
     */
    private fun loadFirstPage() {
        viewModelScope.launch {
            _isLoading.value = true
            _isRefreshing.value = true
            _error.value = null
            try {
                val response = repository.getFeed(tab = tab, cursor = null)
                allAds.clear()
                allAds.addAll(response.items)
                cursor = response.cursor
                hasMore = response.cursor != null
                applyFilter(_activeFilterTags.value)
            } catch (e: Exception) {
                _error.value = e.message ?: "加载失败"
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    /** 加载下一页，将新数据追加到现有列表尾部 */
    private fun loadNextPage() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = repository.getFeed(tab = tab, cursor = cursor)
                allAds.addAll(response.items)
                cursor = response.cursor
                hasMore = response.cursor != null
                applyFilter(_activeFilterTags.value)
            } catch (e: Exception) {
                _error.value = e.message ?: "加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 根据过滤标签过滤广告列表。
     * 过滤逻辑：广告的 aiTags 中必须包含所有选中的过滤标签（AND 逻辑）。
     * 例如同时选中"运动鞋"和"户外"，则只显示同时拥有这两个标签的广告。
     */
    private fun applyFilter(tags: Set<String>) {
        _ads.value = if (tags.isEmpty()) {
            allAds.toList()
        } else {
            allAds.filter { ad ->
                val tagValues = ad.aiTags.map { it.value }.toSet()
                tags.all { tag -> tag in tagValues }
            }
        }
    }
}
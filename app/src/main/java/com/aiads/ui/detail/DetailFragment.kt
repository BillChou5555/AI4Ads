package com.aiads.ui.detail

import com.aiads.data.model.Ad
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.aiads.App
import com.aiads.R
import com.aiads.di.AppContainer
import com.aiads.player.PlayerPool
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.RecyclerView
import android.view.animation.AnimationUtils
import com.aiads.data.local.DatabaseHelper
import com.aiads.data.repository.InteractionRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * 详情页 — M8 将完整实现。
 * 当前为占位，确保 M2 的导航图能编译通过。
 */
class DetailFragment : Fragment() {
    // ===== 参数 =====
    private val adId: String by lazy { arguments?.getString("adId") ?: "" }

    // ===== DI =====
    private val appContainer: AppContainer by lazy {
        (requireActivity().application as App).container
    }
    private val playerPool: PlayerPool by lazy { appContainer.playerPool }
    private val interactionRepository: InteractionRepository by lazy {
        appContainer.interactionRepository }

    // ===== ViewModel =====
    private val viewModel: DetailViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DetailViewModel(appContainer.adCache, adId) as T
            }
        }
    }

    // ===== 状态 =====
    private var mediaPagerAdapter: MediaPagerAdapter? = null
    private var currentPlayer: ExoPlayer? = null
    private var currentVideoPosition = -1
    private lateinit var viewPagerMedia: ViewPager2
    private lateinit var dotIndicator: android.widget.LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvAiSummary: TextView
    private lateinit var tvAdText: TextView
    private lateinit var chipGroupTags: com.google.android.material.chip.ChipGroup
    private lateinit var btnLike: View
    private lateinit var ivLikeIcon: ImageView
    private lateinit var tvLikeCount: TextView
    private lateinit var btnBookmark: View
    private lateinit var ivBookmarkIcon: ImageView
    private lateinit var tvBookmarkCount: TextView
    private lateinit var btnShare: View
    private lateinit var ivShareIcon: ImageView
    private lateinit var tvShareCount: TextView

    // ===== Fragment =====
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 获取控件引用
        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        viewPagerMedia = view.findViewById(R.id.viewPagerMedia)
        dotIndicator = view.findViewById(R.id.dotIndicator)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvAiSummary = view.findViewById(R.id.tvAiSummary)
        tvAdText = view.findViewById(R.id.tvAdText)
        chipGroupTags = view.findViewById(R.id.chipGroupTags)

        // 2. 返回按钮
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // 3. 观察广告数据
        viewModel.ad.observe(viewLifecycleOwner) { ad ->
            ad?.let { bindAd(it) }
        }

        // 4. 交互按钮
        btnLike = view.findViewById(R.id.btnLike)
        ivLikeIcon = view.findViewById(R.id.ivLikeIcon)
        tvLikeCount = view.findViewById(R.id.tvLikeCount)
        btnBookmark = view.findViewById(R.id.btnBookmark)
        ivBookmarkIcon = view.findViewById(R.id.ivBookmarkIcon)
        tvBookmarkCount = view.findViewById(R.id.tvBookmarkCount)
        btnShare = view.findViewById(R.id.btnShare)
        ivShareIcon = view.findViewById(R.id.ivShareIcon)
        tvShareCount = view.findViewById(R.id.tvShareCount)

        btnLike.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_button))
            viewLifecycleOwner.lifecycleScope.launch {
                bindInteractionButtons(interactionRepository.toggleLike(adId))
            }
        }
        btnBookmark.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_button))
            viewLifecycleOwner.lifecycleScope.launch {
                bindInteractionButtons(interactionRepository.toggleBookmark(adId))
            }
        }
        btnShare.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_button))
            viewLifecycleOwner.lifecycleScope.launch {
                bindInteractionButtons(interactionRepository.toggleShare(adId))
            }
        }

        setupSwipeBack()
    }

    // 填充数据
    private fun bindAd(ad: Ad) {
        // 文字信息
        tvTitle.text = ad.title
        tvAiSummary.text = ad.aiSummary
        tvAdText.text = ad.adText

        // 标签
        chipGroupTags.removeAllViews()
        ad.aiTags.forEach { tag ->
            val chip = Chip(chipGroupTags.context).apply {
                text = "${tag.category}: ${tag.value}"
            }
            chipGroupTags.addView(chip)
        }

        // 媒体 ViewPager2
        mediaPagerAdapter = MediaPagerAdapter(viewModel.mediaItems)
        viewPagerMedia.adapter = mediaPagerAdapter

        // 圆点指示器
        // 手动创建圆点
        dotIndicator.removeAllViews()
        val size = (8 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()
        for (i in viewModel.mediaItems.indices) {
            val dot = ImageView(dotIndicator.context).apply {
                setImageResource(if (i == 0) R.drawable.dot_selected else R.drawable.dot_unselected)
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                    leftMargin = margin
                    rightMargin = margin
                }
            }
            dotIndicator.addView(dot)
        }

        // 页面切换时更新圆点
        viewPagerMedia.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                for (i in 0 until dotIndicator.childCount) {
                    (dotIndicator.getChildAt(i) as ImageView).setImageResource(
                        if (i == position) R.drawable.dot_selected else R.drawable.dot_unselected
                    )
                }
            }
        })


        // 视频播放控制
        setupVideoPlayback()

        // 加载交互状态
        viewLifecycleOwner.lifecycleScope.launch {
            bindInteractionButtons(interactionRepository.getInteraction(adId))
        }
    }

    private fun setupVideoPlayback() {
        viewPagerMedia.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                handlePageSelected(position)
            }
        })

        // 初始化首屏视频
        if (viewModel.mediaItems.isNotEmpty()) {
            handlePageSelected(0)
        }
    }

    private fun handlePageSelected(position: Int) {
        val newItem = viewModel.mediaItems[position]

        // 不是视频 → 释放旧播放器
        if (newItem !is MediaItem.Video) {
            releaseCurrentPlayer()
            currentVideoPosition = -1
            return
        }

        // 同一个视频 → 不重复创建
        if (position == currentVideoPosition) return

        // 先释放旧的，再创建新的
        releaseCurrentPlayer()
        currentVideoPosition = position

        val player = playerPool.acquire()
        player.volume = 1.0f      // 详情页有声音
        player.repeatMode = Player.REPEAT_MODE_ALL

        val videoUrl = newItem.url
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUrl))
        player.prepare()
        player.play()

        currentPlayer = player

        // 将 player 挂载到对应页面的 PlayerView 上
        viewPagerMedia.post {
            val recyclerView = viewPagerMedia.getChildAt(0) as? RecyclerView
            val videoHolder = recyclerView?.findViewHolderForAdapterPosition(position)
            if (videoHolder is VideoViewHolder) {
                videoHolder.playerView.player = player
            }
        }
    }

    private fun setupSwipeBack() {
        val swipeLayout = requireView().findViewById<SwipeBackLayout>(R.id.swipeBackLayout)
        swipeLayout.shouldIntercept = { y ->
            y > viewPagerMedia.bottom + dotIndicator.height
        }
        swipeLayout.onSwipeBack = {
            findNavController().navigateUp()
        }
    }

    // 生命周期管理
    override fun onPause() {
        super.onPause()
        releaseCurrentPlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentPlayer?.let { playerPool.release(it) }
        currentPlayer = null
    }

    private fun releaseCurrentPlayer() {
        currentPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
            playerPool.release(player)
        }
        currentPlayer = null
        currentVideoPosition = -1
    }

    private fun bindInteractionButtons(state: DatabaseHelper.InteractionState?) {
        ivLikeIcon.setImageResource(
            if (state?.isLiked == true) R.drawable.ic_like_active else R.drawable.ic_like_inactive
        )
        tvLikeCount.text = if (state?.isLiked == true) "1" else ""

        ivBookmarkIcon.setImageResource(
            if (state?.isBookmarked == true) R.drawable.ic_bookmark_active else
                R.drawable.ic_bookmark_inactive
        )
        tvBookmarkCount.text = if (state?.isBookmarked == true) "1" else ""

        ivShareIcon.setImageResource(
            if (state?.isShared == true) R.drawable.ic_share_active else R.drawable.ic_share_inactive
        )
        tvShareCount.text = if (state?.isShared == true) "1" else ""
    }
}

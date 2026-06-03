package com.aiads.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aiads.R

/**
 * 详情页 — M8 将完整实现。
 * 当前为占位，确保 M2 的导航图能编译通过。
 */
class DetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_feed_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adId = arguments?.getString("adId") ?: "unknown"
        view.findViewById<android.widget.TextView>(R.id.tvTabPlaceholder)?.text =
            "详情页\n\n广告 ID: $adId\n\nM8 将在此实现详情功能"
    }
}

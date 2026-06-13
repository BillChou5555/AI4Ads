package com.aiads.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiads.App
import com.aiads.R
import com.aiads.analytics.AnalyticsManager
import com.aiads.data.local.DatabaseHelper
import com.aiads.data.local.DatabaseHelper.AnalyticsEventRecord
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.LinearLayout

class AnalyticsDashboardFragment : Fragment() {

    private val analyticsManager: AnalyticsManager by lazy {
        (requireActivity().application as App).container.analyticsManager
    }

    private lateinit var tvTotalEvents: TextView
    private lateinit var llEventSummary: LinearLayout
    private lateinit var barChart: BarChart
    private lateinit var recyclerEvents: RecyclerView

    companion object {
        private val EVENT_TYPES = listOf(
            "impression" to "曝光",
            "click" to "点击",
            "like" to "点赞",
            "favorite" to "收藏",
            "share" to "转发",
            "tag_filter" to "标签过滤"
        )
        private val BAR_COLORS = listOf(
            Color.parseColor("#2196F3"),  // 蓝 - 曝光
            Color.parseColor("#4CAF50"),  // 绿 - 点击
            Color.parseColor("#E53935"),  // 红 - 点赞
            Color.parseColor("#FFA000"),  // 黄 - 收藏
            Color.parseColor("#1976D2"),  // 深蓝 - 转发
            Color.parseColor("#9C27B0")   // 紫 - 标签过滤
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_analytics_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        tvTotalEvents = view.findViewById(R.id.tvTotalEvents)
        llEventSummary = view.findViewById(R.id.llEventSummary)
        barChart = view.findViewById(R.id.barChart)
        recyclerEvents = view.findViewById(R.id.recyclerEvents)
        recyclerEvents.layoutManager = LinearLayoutManager(requireContext())

        loadData()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val counts = withContext(Dispatchers.IO) { analyticsManager.getEventCountsByType() }
            val events = withContext(Dispatchers.IO) { analyticsManager.getAllEvents() }

            // 概览
            val total = counts.values.sum()
            tvTotalEvents.text = "总事件数: $total"
            llEventSummary.removeAllViews()
            for ((key, label) in EVENT_TYPES) {
                val tv = TextView(requireContext()).apply {
                    text = "$label: ${counts[key] ?: 0}"
                    textSize = 14f
                    setTextColor(Color.parseColor("#666666"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                llEventSummary.addView(tv)
            }

            // 柱状图
            setupBarChart(counts)

            // 最近事件列表
            recyclerEvents.adapter = EventListAdapter(events.take(50))
        }
    }

    private fun setupBarChart(counts: Map<String, Int>) {
        val entries = EVENT_TYPES.mapIndexed { index, (key, _) ->
            BarEntry(index.toFloat(), (counts[key] ?: 0).toFloat())
        }

        val dataSet = BarDataSet(entries, "事件计数").apply {
            colors = BAR_COLORS
            valueTextSize = 12f
            valueTextColor = Color.BLACK
        }

        barChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            animateY(500)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(EVENT_TYPES.map { it.second })
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textSize = 12f
            }
            axisLeft.apply {
                granularity = 1f
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            invalidate()
        }
    }
}

/** RecyclerView Adapter: 展示最近埋点事件 */
class EventListAdapter(private val events: List<AnalyticsEventRecord>) :
    RecyclerView.Adapter<EventListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEventType: TextView = view.findViewById(R.id.tvEventType)
        val tvDetail: TextView = view.findViewById(R.id.tvDetail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analytics_event, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = events.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        val typeLabels = mapOf(
            "impression" to "曝光", "click" to "点击", "like" to "点赞",
            "favorite" to "收藏", "share" to "转发", "tag_filter" to "标签过滤"
        )
        holder.tvEventType.text = typeLabels[event.eventType] ?: event.eventType
        holder.tvDetail.text = "广告: ${event.adId.take(8)}... | ${formatTime(event.eventTime)}"
    }

    private fun formatTime(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(millis))
    }
}
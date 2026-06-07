package com.aiads.ui.detail

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

class SwipeBackLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** 判断当前触摸位置是否需要拦截，由 Fragment 注入 */
    var shouldIntercept: ((y: Float) -> Boolean)? = null
    /** 滑动超过 1/3 屏幕时回调，由 Fragment 注入 navigateUp */
    var onSwipeBack: (() -> Unit)? = null

    private var startX = 0f
    private var startY = 0f
    private var tracking = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val check = shouldIntercept ?: return false

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                tracking = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!check(ev.y)) return false
                val dx = ev.x - startX
                val dy = abs(ev.y - startY)
                if (!tracking && dx > touchSlop && dx > dy) {
                    tracking = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true   // 拦截！后续事件进 onTouchEvent
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!tracking) return false

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                translationX = (event.x - startX).coerceAtLeast(0f)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (translationX > width / 8f) {
                    onSwipeBack?.invoke()
                } else {
                    animate().translationX(0f).setDuration(200).start()
                }
                tracking = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
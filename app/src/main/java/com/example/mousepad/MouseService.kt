package com.example.mousepad

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs
import kotlin.math.hypot

class MouseService : AccessibilityService() {

    companion object {
        var instance: MouseService? = null
        const val SENSITIVITY = 1.8f
        const val NATURAL_SCROLL = true
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var screenW = 0
    private var screenH = 0
    private var cursorX = 0f
    private var cursorY = 0f
    private var editing = false
    val editMode: Boolean get() = editing

    private var cursorView: CursorView? = null
    private var padView: PadView? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var padParams: WindowManager.LayoutParams? = null
    private var scrolling = false

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onServiceConnected() {
        super.onServiceConnected()
        cleanup()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        updateScreenSize()
        cursorX = screenW / 2f
        cursorY = screenH / 2f
        createCursor()
        createPad()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        try {
            cursorView?.let { wm.removeView(it) }
        } catch (e: Exception) {
        }
        try {
            padView?.let { wm.removeView(it) }
        } catch (e: Exception) {
        }
        cursorView = null
        padView = null
        if (instance === this) instance = null
    }

    @Suppress("DEPRECATION")
    private fun updateScreenSize() {
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            screenW = b.width()
            screenH = b.height()
        } else {
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            screenW = dm.widthPixels
            screenH = dm.heightPixels
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (cursorView == null || padView == null) return
        try {
            updateScreenSize()
            val cp = cursorParams!!
            cp.width = screenW
            cp.height = screenH
            wm.updateViewLayout(cursorView, cp)
            cursorX = cursorX.coerceIn(0f, (screenW - 1).toFloat())
            cursorY = cursorY.coerceIn(0f, (screenH - 1).toFloat())
            val pp = padParams!!
            pp.width = minOf(pp.width, screenW)
            pp.height = minOf(pp.height, screenH)
            pp.x = pp.x.coerceIn(0, screenW - pp.width)
            pp.y = pp.y.coerceIn(0, screenH - pp.height)
            wm.updateViewLayout(padView, pp)
            cursorView?.invalidate()
        } catch (e: Exception) {
        }
    }

    private fun createCursor() {
        val v = CursorView(this)
        val lp = WindowManager.LayoutParams(
            screenW,
            screenH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0
        lp.y = 0
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        cursorParams = lp
        cursorView = v
        wm.addView(v, lp)
    }

    private fun createPad() {
        val sp = getSharedPreferences("pad", Context.MODE_PRIVATE)
        val minPad = dp(120f).toInt()
        val defW = (screenW * 0.9f).toInt()
        val defH = (screenH * 0.35f).toInt()
        val w = sp.getInt("w", defW).coerceIn(minPad, screenW)
        val h = sp.getInt("h", defH).coerceIn(minPad, screenH)
        val x = sp.getInt("x", (screenW - defW) / 2).coerceIn(0, screenW - w)
        val y = sp.getInt("y", screenH - defH - dp(80f).toInt()).coerceIn(0, screenH - h)

        val v = PadView(this)
        val lp = WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = x
        lp.y = y
        padParams = lp
        padView = v
        wm.addView(v, lp)
    }

    private fun savePad() {
        val p = padParams ?: return
        getSharedPreferences("pad", Context.MODE_PRIVATE).edit()
            .putInt("x", p.x)
            .putInt("y", p.y)
            .putInt("w", p.width)
            .putInt("h", p.height)
            .apply()
    }

    fun setEditMode(on: Boolean) {
        editing = on
        padView?.invalidate()
        if (!on) savePad()
    }

    private fun moveCursor(dx: Float, dy: Float) {
        cursorX = (cursorX + dx).coerceIn(0f, (screenW - 1).toFloat())
        cursorY = (cursorY + dy).coerceIn(0f, (screenH - 1).toFloat())
        cursorView?.invalidate()
    }

    private fun click(isLong: Boolean) {
        val p = Path()
        p.moveTo(cursorX, cursorY)
        val d = if (isLong) 700L else 60L
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, d))
            .build()
        dispatchGesture(g, null, null)
    }

    private fun scrollBy(dx: Float, dy: Float): Boolean {
        if (scrolling) return false
        val maxLen = dp(300f)
        val len = hypot(dx, dy)
        if (len < 5f) return true
        val k = if (len > maxLen) maxLen / len else 1f
        val ex = (cursorX + dx * k).coerceIn(1f, (screenW - 2).toFloat())
        val ey = (cursorY + dy * k).coerceIn(1f, (screenH - 2).toFloat())
        val p = Path()
        p.moveTo(cursorX, cursorY)
        p.lineTo(ex, ey)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 160))
            .build()
        scrolling = true
        val ok = dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                scrolling = false
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                scrolling = false
            }
        }, null)
        if (!ok) scrolling = false
        return ok
    }

    inner class CursorView(c: Context) : View(c) {
        private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.3f
            strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            val s = dp(1f)
            canvas.save()
            canvas.translate(cursorX, cursorY)
            canvas.scale(s, s)
            path.reset()
            path.moveTo(0f, 0f)
            path.lineTo(0f, 17f)
            path.lineTo(4.2f, 13.2f)
            path.lineTo(7.2f, 20.5f)
            path.lineTo(10.2f, 19.2f)
            path.lineTo(7.2f, 12.2f)
            path.lineTo(12.2f, 12.2f)
            path.close()
            canvas.drawPath(path, fillP)
            canvas.drawPath(path, strokeP)
            canvas.restore()
        }
    }

    inner class PadView(c: Context) : View(c) {
        private val slop = ViewConfiguration.get(c).scaledTouchSlop.toFloat()
        private val edgePx = dp(28f)
        private val swipePx = dp(70f)
        private val handlePx = dp(56f)
        private val minPad = dp(120f)

        private val fillP = Paint().apply {
            color = Color.argb(60, 0, 170, 255)
            style = Paint.Style.FILL
        }
        private val borderP = Paint().apply {
            color = Color.rgb(0, 170, 255)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        private val handleP = Paint().apply {
            color = Color.rgb(0, 170, 255)
            style = Paint.Style.FILL
        }

        // حالت‌ها: 0 هیچ، 1 نشانگر، 2 لبه، 3 اسکرول، 4 نادیده
        private var mode = 0
        private var edgeKind = 0
        private var edgeDone = false
        private var downX = 0f
        private var downY = 0f
        private var downTime = 0L
        private var lastX = 0f
        private var lastY = 0f
        private var moved = false
        private var longFired = false
        private var lastAvgX = 0f
        private var lastAvgY = 0f
        private var accX = 0f
        private var accY = 0f

        // ویرایش
        private var editKind = 0
        private var lastRawX = 0f
        private var lastRawY = 0f

        private val longRunnable = Runnable {
            if (mode == 1 && !moved) {
                longFired = true
                click(true)
            }
        }

        override fun onDraw(canvas: Canvas) {
            if (!editing) return
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, fillP)
            canvas.drawRect(2f, 2f, w - 2f, h - 2f, borderP)
            canvas.drawRect(w - handlePx, h - handlePx, w, h, handleP)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            return if (editing) handleEdit(e) else handlePad(e)
        }

        private fun avgX(e: MotionEvent): Float {
            var s = 0f
            for (i in 0 until e.pointerCount) s += e.getX(i)
            return s / e.pointerCount
        }

        private fun avgY(e: MotionEvent): Float {
            var s = 0f
            for (i in 0 until e.pointerCount) s += e.getY(i)
            return s / e.pointerCount
        }

        private fun handleEdit(e: MotionEvent): Boolean {
            val pp = padParams ?: return true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    editKind = if (e.x > width - handlePx && e.y > height - handlePx) 2 else 1
                    lastRawX = e.rawX
                    lastRawY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - lastRawX).toInt()
                    val dy = (e.rawY - lastRawY).toInt()
                    lastRawX = e.rawX
                    lastRawY = e.rawY
                    if (editKind == 1) {
                        pp.x = (pp.x + dx).coerceIn(0, screenW - pp.width)
                        pp.y = (pp.y + dy).coerceIn(0, screenH - pp.height)
                    } else {
                        pp.width = (pp.width + dx).coerceIn(minPad.toInt(), screenW - pp.x)
                        pp.height = (pp.height + dy).coerceIn(minPad.toInt(), screenH - pp.y)
                    }
                    try {
                        wm.updateViewLayout(this, pp)
                    } catch (ex: Exception) {
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    savePad()
                }
            }
            return true
        }

        private fun handlePad(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                    lastX = e.x
                    lastY = e.y
                    downTime = e.eventTime
                    moved = false
                    longFired = false
                    edgeDone = false
                    edgeKind = when {
                        e.x < edgePx -> 1
                        e.x > width - edgePx -> 2
                        e.y > height - edgePx -> 3
                        else -> 0
                    }
                    mode = if (edgeKind != 0) 2 else 1
                    handler.removeCallbacks(longRunnable)
                    if (mode == 1) handler.postDelayed(longRunnable, 500)
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (e.pointerCount >= 2) {
                        handler.removeCallbacks(longRunnable)
                        mode = 3
                        moved = true
                        lastAvgX = avgX(e)
                        lastAvgY = avgY(e)
                        accX = 0f
                        accY = 0f
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    mode = 4
                }

                MotionEvent.ACTION_MOVE -> {
                    when (mode) {
                        1 -> {
                            val dx = e.x - lastX
                            val dy = e.y - lastY
                            if (!moved) {
                                if (hypot(e.x - downX, e.y - downY) > slop) {
                                    moved = true
                                    handler.removeCallbacks(longRunnable)
                                    moveCursor(
                                        (e.x - downX) * SENSITIVITY,
                                        (e.y - downY) * SENSITIVITY
                                    )
                                }
                            } else {
                                moveCursor(dx * SENSITIVITY, dy * SENSITIVITY)
                            }
                            lastX = e.x
                            lastY = e.y
                        }

                        2 -> {
                            val dx = e.x - downX
                            val dy = e.y - downY
                            if (hypot(dx, dy) > slop) moved = true
                            if (!edgeDone) {
                                var act = -1
                                when (edgeKind) {
                                    1 -> if (dx > swipePx && abs(dy) < dx) {
                                        act = AccessibilityService.GLOBAL_ACTION_BACK
                                    }
                                    2 -> if (-dx > swipePx && abs(dy) < -dx) {
                                        act = AccessibilityService.GLOBAL_ACTION_RECENTS
                                    }
                                    3 -> if (-dy > swipePx && abs(dx) < -dy) {
                                        act = AccessibilityService.GLOBAL_ACTION_HOME
                                    }
                                }
                                if (act != -1) {
                                    edgeDone = true
                                    performGlobalAction(act)
                                }
                            }
                        }

                        3 -> {
                            val ax = avgX(e)
                            val ay = avgY(e)
                            accX += ax - lastAvgX
                            accY += ay - lastAvgY
                            lastAvgX = ax
                            lastAvgY = ay
                            if (hypot(accX, accY) > dp(24f)) {
                                val sign = if (NATURAL_SCROLL) 1f else -1f
                                val sent = scrollBy(accX * sign * 2f, accY * sign * 2f)
                                if (sent) {
                                    accX = 0f
                                    accY = 0f
                                }
                            }
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longRunnable)
                    val quick = e.eventTime - downTime < 350
                    if ((mode == 1 || mode == 2) && !moved && !longFired && quick) {
                        click(false)
                    }
                    mode = 0
                }

                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longRunnable)
                    mode = 0
                }
            }
            return true
        }
    }
}

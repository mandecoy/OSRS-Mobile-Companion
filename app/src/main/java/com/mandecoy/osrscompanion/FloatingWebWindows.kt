package com.mandecoy.osrscompanion

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Two independently resizable, draggable Android WebView overlays.
 *
 * Browser content never has automatic keyboard focus. A deliberate keyboard
 * button temporarily enables focus, and turning it off gives focus back to
 * OSRS. The windows are real webpages, not scraped or simplified content.
 */
internal class FloatingWebWindows(
    private val context: Context,
    private val manager: WindowManager,
    private val onEmpty: () -> Unit
) {
    private data class Window(
        val slot: String,
        val root: LinearLayout,
        val browser: WebView,
        val body: LinearLayout,
        val title: TextView,
        val minimizeControl: TextView,
        val keyboardControl: TextView,
        val params: WindowManager.LayoutParams,
        var minimized: Boolean = false,
        var keyboardMode: Boolean = false,
        var expandedWidth: Int,
        var expandedHeight: Int
    )

    private val windows = linkedMapOf<String, Window>()
    val hasWindows: Boolean get() = windows.isNotEmpty()

    fun open(slot: String, title: String, url: String) {
        if (!url.startsWith("https://")) return
        val existing = windows[slot]
        if (existing != null) {
            if (existing.browser.url != url) existing.browser.loadUrl(url)
            if (existing.minimized) toggleMinimize(existing)
            // Explicitly bring the selected window in front.
            try {
                manager.removeView(existing.root)
                manager.addView(existing.root, existing.params)
            } catch (_: Exception) { }
            return
        }
        // In single-window mode, opening another page replaces the old popup.
        // In dual mode only two windows are retained. Reusing the oldest slot
        // prevents invisible third/fourth floating layers accumulating.
        val limit = if (FloatingPrefs.dualWebWindows(context)) 2 else 1
        while (windows.size >= limit) close(windows.keys.first())

        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels.coerceAtLeast(dp(240))
        val screenHeight = metrics.heightPixels.coerceAtLeast(dp(240))
        val defaultWidth = minOf(dp(330), screenWidth - dp(16))
        val defaultHeight = minOf(dp(415), screenHeight - dp(90))
        val saved = FloatingPrefs.webWindowGeometry(context, slot)
        val remember = FloatingPrefs.rememberWebPositions(context)
        val width = (if (remember) saved?.width else null ?: defaultWidth)
            .coerceIn(minOf(dp(190), screenWidth - dp(8)), screenWidth - dp(8))
        val height = (if (remember) saved?.height else null ?: defaultHeight)
            .coerceIn(minOf(dp(180), screenHeight - dp(8)), screenHeight - dp(8))
        val x = (if (remember) saved?.x else null ?: if (windows.isEmpty()) dp(8) else dp(38))
            .coerceIn(0, (screenWidth - width).coerceAtLeast(0))
        val y = (if (remember) saved?.y else null ?: if (windows.isEmpty()) dp(72) else dp(112))
            .coerceIn(0, (screenHeight - height).coerceAtLeast(0))

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.rgb(35, 30, 25))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.rgb(215, 169, 63))
            }
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), 0, dp(2), 0)
        }
        val titleText = TextView(context).apply {
            text = "⠿ " + title.take(22)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(244, 234, 212))
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        header.addView(titleText, LinearLayout.LayoutParams(0, dp(41), 1f))
        fun action(symbol: String, hint: String, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                text = symbol
                textSize = 17f
                setTextColor(Color.rgb(215, 169, 63))
                gravity = Gravity.CENTER
                contentDescription = hint
                setOnClickListener { onClick() }
            }.also { header.addView(it, LinearLayout.LayoutParams(dp(37), dp(41))) }
        }

        val browser = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            setBackgroundColor(Color.WHITE)
            webViewClient = WebViewClient()
        }
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(browser, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, dp(4), 0)
        }
        val back = TextView(context).apply {
            text = "◀"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Previous webpage"
            setOnClickListener { if (browser.canGoBack()) browser.goBack() }
        }
        val forward = TextView(context).apply {
            text = "▶"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Next webpage"
            setOnClickListener { if (browser.canGoForward()) browser.goForward() }
        }
        footer.addView(back, LinearLayout.LayoutParams(dp(42), dp(33)))
        footer.addView(forward, LinearLayout.LayoutParams(dp(42), dp(33)))
        val swap = TextView(context).apply {
            text = "⇄ Swap"
            textSize = 12f
            setTextColor(Color.rgb(215, 169, 63))
            gravity = Gravity.CENTER
            contentDescription = "Swap two floating windows"
            setOnClickListener { swapWindows() }
        }
        footer.addView(swap, LinearLayout.LayoutParams(0, dp(33), 1f))
        val grip = TextView(context).apply {
            text = "◢"
            textSize = 23f
            setTextColor(Color.rgb(215, 169, 63))
            gravity = Gravity.RIGHT or Gravity.BOTTOM
            contentDescription = "Drag to resize this floating window"
        }
        footer.addView(grip, LinearLayout.LayoutParams(dp(45), dp(33)))
        body.addView(footer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(33)
        ))
        root.addView(header)
        root.addView(body, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val params = WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            alpha = FloatingPrefs.webOpacity(context) / 100f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        // Controls are declared before this Window object is installed.
        // Lambdas resolve the live window by slot when first clicked.
        action("⌨", "Enable keyboard for searching this webpage") {
            windows[slot]?.let { toggleKeyboard(it) }
        }
        action("–", "Minimize floating webpage") {
            windows[slot]?.let { toggleMinimize(it) }
        }
        action("×", "Close floating webpage") { close(slot) }
        val keyboardButton = header.getChildAt(header.childCount - 3) as TextView
        val minimizeButton = header.getChildAt(header.childCount - 2) as TextView

        fun drag(view: View, resize: Boolean) {
            var startX = 0
            var startY = 0
            var startW = 0
            var startH = 0
            var touchX = 0f
            var touchY = 0f
            view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        startW = params.width
                        startH = params.height
                        touchX = event.rawX
                        touchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        val target = windows[slot] ?: return@setOnTouchListener true
                        if (resize) {
                            val minW = minOf(dp(190), screenWidth - dp(8))
                            val minH = minOf(dp(180), screenHeight - dp(8))
                            params.width = (startW + dx).coerceIn(minW, (screenWidth - params.x).coerceAtLeast(minW))
                            params.height = (startH + dy).coerceIn(minH, (screenHeight - params.y).coerceAtLeast(minH))
                            target.expandedWidth = params.width
                            target.expandedHeight = params.height
                        } else {
                            params.x = (startX + dx).coerceIn(0, (screenWidth - params.width).coerceAtLeast(0))
                            params.y = (startY + dy).coerceIn(0, (screenHeight - params.height).coerceAtLeast(0))
                        }
                        try { manager.updateViewLayout(root, params) } catch (_: Exception) {}
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        windows[slot]?.let { saveGeometry(it) }
                        true
                    }
                    else -> false
                }
            }
        }
        drag(titleText, false)
        drag(grip, true)

        val popup = Window(
            slot, root, browser, body, titleText, minimizeButton, keyboardButton,
            params, expandedWidth = width, expandedHeight = height
        )
        windows[slot] = popup
        try {
            manager.addView(root, params)
            browser.loadUrl(url)
        } catch (_: Exception) {
            windows.remove(slot)
            browser.destroy()
            onEmpty()
        }
    }

    fun swapWindows() {
        if (windows.size < 2) return
        val first = windows.values.elementAt(0)
        val second = windows.values.elementAt(1)
        val x = first.params.x
        val y = first.params.y
        first.params.x = second.params.x
        first.params.y = second.params.y
        second.params.x = x
        second.params.y = y
        for (w in listOf(first, second)) {
            clamp(w)
            try { manager.updateViewLayout(w.root, w.params) } catch (_: Exception) { }
            saveGeometry(w)
        }
    }

    fun applySettings() {
        if (!FloatingPrefs.dualWebWindows(context)) {
            while (windows.size > 1) close(windows.keys.first())
        }
        windows.values.forEach {
            it.params.alpha = FloatingPrefs.webOpacity(context) / 100f
            try { manager.updateViewLayout(it.root, it.params) } catch (_: Exception) {}
        }
    }

    fun closeAll() {
        windows.keys.toList().forEach { close(it) }
    }

    fun close(slot: String) {
        val win = windows.remove(slot) ?: return
        stopKeyboard(win)
        try { manager.removeView(win.root) } catch (_: Exception) {}
        win.browser.stopLoading()
        win.browser.destroy()
        if (windows.isEmpty()) onEmpty()
    }

    private fun toggleMinimize(win: Window) {
        if (!FloatingPrefs.webMinimizeEnabled(context)) {
            close(win.slot)
            return
        }
        if (win.minimized) {
            win.params.width = win.expandedWidth
            win.params.height = win.expandedHeight
            win.body.visibility = View.VISIBLE
            win.minimizeControl.text = "–"
        } else {
            stopKeyboard(win)
            win.body.visibility = View.GONE
            win.params.width = minOf(dp(220), context.resources.displayMetrics.widthPixels - dp(8))
            win.params.height = dp(42)
            win.minimizeControl.text = "▢"
        }
        win.minimized = !win.minimized
        clamp(win)
        try { manager.updateViewLayout(win.root, win.params) } catch (_: Exception) {}
        saveGeometry(win)
    }

    private fun toggleKeyboard(win: Window) {
        if (win.keyboardMode) {
            stopKeyboard(win)
        } else {
            win.keyboardMode = true
            win.params.flags = win.params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            win.keyboardControl.text = "✓"
            try {
                manager.updateViewLayout(win.root, win.params)
                win.browser.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(win.browser, InputMethodManager.SHOW_IMPLICIT)
            } catch (_: Exception) {}
        }
    }

    private fun stopKeyboard(win: Window) {
        if (!win.keyboardMode) return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(win.browser.windowToken, 0)
        win.browser.clearFocus()
        win.params.flags = win.params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        win.keyboardMode = false
        win.keyboardControl.text = "⌨"
        try { manager.updateViewLayout(win.root, win.params) } catch (_: Exception) {}
    }

    private fun clamp(win: Window) {
        val metrics = context.resources.displayMetrics
        win.params.x = win.params.x.coerceIn(0, (metrics.widthPixels - win.params.width).coerceAtLeast(0))
        win.params.y = win.params.y.coerceIn(0, (metrics.heightPixels - win.params.height - dp(8)).coerceAtLeast(0))
    }

    private fun saveGeometry(win: Window) {
        if (!FloatingPrefs.rememberWebPositions(context)) return
        FloatingPrefs.saveWebWindowGeometry(
            context, win.slot,
            FloatingPrefs.WebGeometry(win.params.x, win.params.y,
                win.expandedWidth, win.expandedHeight)
        )
    }

    private fun dp(value: Int): Int =
        (context.resources.displayMetrics.density * value).toInt()
}

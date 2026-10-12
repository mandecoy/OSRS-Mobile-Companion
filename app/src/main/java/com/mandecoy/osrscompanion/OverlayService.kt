package com.mandecoy.osrscompanion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button as AndroidButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max

private val FloatStone = Color(0xff2e2924)
private val FloatGold = Color(0xffd7a93f)
private val FloatGoldDark = Color(0xff8b6722)
private val FloatParchment = Color(0xffd7c7a4)
private val FloatText = Color(0xfff4ead4)

object FloatingPrefs {
    private const val PREFS = "floating_tools_v1"

    fun name(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("name", "AFK check") ?: "AFK check"

    fun seconds(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("seconds", 300L).coerceAtLeast(1L)

    fun repeat(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("repeat", false)

    // Persist the player's chosen popout size across timer restarts.
    fun compactTimer(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("timer_compact", false)

    fun setCompactTimer(context: Context, compact: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("timer_compact", compact).apply()
    }

    fun save(context: Context, name: String, seconds: Long, repeat: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("name", name)
            .putLong("seconds", seconds.coerceAtLeast(1L))
            .putBoolean("repeat", repeat)
            .apply()
    }

    fun timerPosition(context: Context): Pair<Int, Int> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getInt("timer_x", 20) to p.getInt("timer_y", 180)
    }

    fun bubblePosition(context: Context): Pair<Int, Int> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getInt("bubble_x", 20) to p.getInt("bubble_y", 420)
    }

    fun saveTimerPosition(context: Context, x: Int, y: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("timer_x", x).putInt("timer_y", y).apply()
    }

    fun saveBubblePosition(context: Context, x: Int, y: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("bubble_x", x).putInt("bubble_y", y).apply()
    }
}

class FloatingOverlayService : Service() {
    companion object {
        const val ACTION_SHOW_COMPANION = "osrs.overlay.SHOW_COMPANION"
        const val ACTION_SHOW_PUZZLE = "osrs.overlay.SHOW_PUZZLE"
        const val ACTION_HIDE_COMPANION = "osrs.overlay.HIDE_COMPANION"
        const val ACTION_START_TIMER = "osrs.overlay.START_TIMER"
        const val ACTION_STOP_TIMER = "osrs.overlay.STOP_TIMER"
        const val ACTION_STOP_ALL = "osrs.overlay.STOP_ALL"
        private const val CHANNEL = "osrs_floating_tools"
        private const val NOTIFICATION_ID = 5050
    }

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var bubbleView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var menuView: LinearLayout? = null
    private var puzzleView: LinearLayout? = null
    private var timerView: LinearLayout? = null
    private var timerParams: WindowManager.LayoutParams? = null
    private var countdownText: TextView? = null
    private var compactCountdownText: TextView? = null
    private var compactTimerPanel: LinearLayout? = null
    private var mediumTimerPanel: LinearLayout? = null
    private var timerTitleText: TextView? = null
    private var pauseButton: AndroidButton? = null
    private var timerStatusText: TextView? = null
    private var timerSettingText: TextView? = null
    private var timerEditPanel: LinearLayout? = null
    private var timerEndAt = 0L
    private var timerRemaining = 0L
    private var timerDuration = 0L
    private var timerLabel = "AFK check"
    private var timerRepeat = false
    private var timerPaused = false

    private val timerTick = object : Runnable {
        override fun run() {
            if (timerView == null) return
            if (!timerPaused) {
                val remaining = max(0L, timerEndAt - System.currentTimeMillis())
                timerRemaining = remaining
                refreshTimerDisplay()
                if (remaining <= 0L) {
                    signalTimer()
                    if (timerRepeat) {
                        timerEndAt = System.currentTimeMillis() + timerDuration
                        timerRemaining = timerDuration
                        refreshTimerDisplay()
                    } else {
                        stopTimerOverlay()
                        maybeStopService()
                        return
                    }
                }
            }
            handler.postDelayed(this, 250L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ensureChannel()
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                5051,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("OSRS Companion overlay")
            .setContentText("Floating controls are active")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_SHOW_COMPANION -> showCompanionBubble()
            ACTION_SHOW_PUZZLE -> showPuzzleBubble()
            ACTION_HIDE_COMPANION -> hideCompanionBubble()
            ACTION_START_TIMER -> startTimerFromPrefs()
            ACTION_STOP_TIMER -> {
                stopTimerOverlay()
                maybeStopService()
            }
            ACTION_STOP_ALL -> {
                stopTimerOverlay()
                hideCompanionBubble()
                hidePuzzleBubble()
                stopSelf()
            }
            else -> showCompanionBubble()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeViewSafely(menuView)
        removeViewSafely(puzzleView)
        removeViewSafely(timerView)
        removeViewSafely(bubbleView)
        menuView = null
        puzzleView = null
        timerView = null
        bubbleView = null
        super.onDestroy()
    }

    private fun overlayParams(width: Int, height: Int, x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= 26) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun showCompanionBubble() {
        if (bubbleView != null) return
        val pos = FloatingPrefs.bubblePosition(this)
        val view = TextView(this).apply {
            text = "OSRS"
            gravity = Gravity.CENTER
            setTextColor(AndroidColor.BLACK)
            textSize = 12f
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = roundedBackground(0xffd7a93f.toInt(), 28f)
        }
        val params = overlayParams(dp(56), dp(56), pos.first, pos.second)
        bubbleView = view
        bubbleParams = params
        makeDraggable(view, params, onMoveFinished = {
            FloatingPrefs.saveBubblePosition(this, params.x, params.y)
        }) {
            toggleCompanionMenu()
        }
        windowManager.addView(view, params)
    }

    private fun hideCompanionBubble() {
        removeViewSafely(menuView)
        menuView = null
        removeViewSafely(bubbleView)
        bubbleView = null
        bubbleParams = null
        maybeStopService()
    }

    private fun toggleCompanionMenu() {
        if (menuView != null) {
            removeViewSafely(menuView)
            menuView = null
            return
        }
        val bubble = bubbleParams ?: return
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBackground(0xff25201b.toInt(), 14f)
        }
        fun menuItem(label: String, action: () -> Unit) {
            val item = overlayButton(label, action)
            layout.addView(item, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
            ).apply { bottomMargin = dp(5) })
        }
        menuItem("Show / Edit Timer") {
            if (timerView == null) startTimerFromPrefs()
            showTimerEditor()
            removeViewSafely(menuView)
            menuView = null
        }
        menuItem("Puzzle Box") {
            showPuzzleBubble()
            removeViewSafely(menuView)
            menuView = null
        }
        menuItem("Restart Timer") { restartTimer() }
        menuItem("Hide Menu") {
            removeViewSafely(menuView)
            menuView = null
        }
        menuItem("Close Bubble") { hideCompanionBubble() }

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val x = (bubble.x + dp(62)).coerceIn(0, (screenW - dp(202)).coerceAtLeast(0))
        val y = bubble.y.coerceIn(0, (screenH - dp(290)).coerceAtLeast(0))
        val params = overlayParams(dp(198), WindowManager.LayoutParams.WRAP_CONTENT, x, y)
        menuView = layout
        windowManager.addView(layout, params)
    }

    /**
     * All puzzle interactions happen through TYPE_APPLICATION_OVERLAY windows
     * created with FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCH_MODAL. Neither the
     * five-move arrows nor the drag handle launches or focuses our Activity.
     * OSRS therefore keeps its foreground/input focus while playing.
     */
    private fun showPuzzleBubble() {
        val solved = PuzzleBubbleStore.load(this)
        if (solved == null) {
            android.widget.Toast.makeText(
                this, "Upload a puzzle screenshot inside Companion first",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        removeViewSafely(puzzleView)
        puzzleView = null

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBackground(0xf5201c18.toInt(), 13f)
        }
        val title = TextView(this).apply {
            text = "PUZZLE  ·  DRAG HERE"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xffd7a93f.toInt())
            setPadding(dp(4), dp(5), dp(4), dp(8))
        }
        root.addView(title)

        val position = FloatingPrefs.bubblePosition(this)
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val x = (position.first + dp(58)).coerceIn(0, (screenWidth - dp(300)).coerceAtLeast(0))
        val y = position.second.coerceIn(0, (screenHeight - dp(390)).coerceAtLeast(0))
        val params = overlayParams(dp(296), WindowManager.LayoutParams.WRAP_CONTENT, x, y)

        val picture = PuzzleBubbleBoard(this)
        root.addView(picture, LinearLayout.LayoutParams(dp(278), dp(278)))

        val counter = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(AndroidColor.WHITE)
            textSize = 14f
            setPadding(dp(2), dp(7), dp(2), dp(5))
        }
        root.addView(counter)

        val steps = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(0xffd7c7a4.toInt())
            textSize = 10f
            setPadding(dp(2), dp(2), dp(2), dp(5))
        }
        root.addView(steps)
        var index = PuzzleBubbleStore.start(this).coerceIn(0, solved.moves.size)
        fun redraw() {
            picture.show(solved, index)
            val last = minOf(index + 5, solved.moves.size)
            counter.text = if (index < solved.moves.size) {
                "Moves " + (index + 1) + "–" + last + " / " + solved.moves.size
            } else "Puzzle complete — " + solved.moves.size + " moves"
            val instructions = PuzzleEngine.moveHints(
                solved.board, solved.moves, index, 5
            )
            steps.text = instructions.joinToString("   ") {
                it.moveNumber.toString() + it.arrow
            }
            PuzzleBubbleStore.setStart(this, index)
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val previous = overlayButton("← Previous 5") {
            index = (index - 5).coerceAtLeast(0)
            redraw()
        }
        val next = overlayButton("Next 5 →") {
            index = (index + 5).coerceAtMost(solved.moves.size)
            redraw()
        }
        val hide = overlayButton("✕") { hidePuzzleBubble() }
        row.addView(previous, LinearLayout.LayoutParams(0, dp(42), 1f))
        row.addView(next, LinearLayout.LayoutParams(0, dp(42), 1f))
        row.addView(hide, LinearLayout.LayoutParams(dp(38), dp(42)))
        root.addView(row)

        // Dragging the title moves the overlay without transferring keyboard or
        // input focus from the game to the Companion application.
        var downX = 0f
        var downY = 0f
        var originX = 0
        var originY = 0
        title.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    originX = params.x
                    originY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (originX + (event.rawX - downX).toInt()).coerceAtLeast(0)
                    params.y = (originY + (event.rawY - downY).toInt()).coerceAtLeast(0)
                    try { windowManager.updateViewLayout(root, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }
        puzzleView = root
        redraw()
        try {
            windowManager.addView(root, params)
        } catch (_: Exception) {
            puzzleView = null
        }
    }

    private fun hidePuzzleBubble() {
        removeViewSafely(puzzleView)
        puzzleView = null
        maybeStopService()
    }

    private fun startTimerFromPrefs() {
        timerLabel = FloatingPrefs.name(this)
        timerDuration = FloatingPrefs.seconds(this) * 1000L
        timerRepeat = FloatingPrefs.repeat(this)
        timerPaused = false
        timerRemaining = timerDuration
        timerEndAt = System.currentTimeMillis() + timerDuration

        if (timerView == null) createTimerOverlay()
        refreshTimerDisplay()
        handler.removeCallbacks(timerTick)
        handler.post(timerTick)
    }

    /**
     * Two tiny, non-focusable OSRS overlays. Medium: compact controls and an
     * optional editor. Compact: only the live clock, eye icon and drag surface.
     * Switching views never recreates a timer or updates timerEndAt.
     */
    private fun createTimerOverlay() {
        val displayWidth = resources.displayMetrics.widthPixels
        val mediumWidth = minOf(dp(192), displayWidth - dp(16))
        val compactWidth = minOf(dp(142), displayWidth - dp(16))
        var compact = FloatingPrefs.compactTimer(this)
        val saved = FloatingPrefs.timerPosition(this)
        val panelWidth = if (compact) compactWidth else mediumWidth
        val params = overlayParams(
            panelWidth, WindowManager.LayoutParams.WRAP_CONTENT,
            saved.first.coerceIn(0, (displayWidth - panelWidth).coerceAtLeast(0)),
            saved.second.coerceIn(
                0, (resources.displayMetrics.heightPixels - dp(250)).coerceAtLeast(0)
            )
        )
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = roundedBackground(0xff211c17.toInt(), 12f)
        }

        // All mode updates are done in-place in the same WindowManager window.
        fun fitWindow() {
            root.post {
                if (timerView !== root) return@post
                params.width = if (compact) compactWidth else mediumWidth
                params.x = params.x.coerceIn(
                    0, (resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0)
                )
                params.y = params.y.coerceIn(
                    0, (resources.displayMetrics.heightPixels - root.height - dp(20)).coerceAtLeast(0)
                )
                try {
                    windowManager.updateViewLayout(root, params)
                    FloatingPrefs.saveTimerPosition(this, params.x, params.y)
                } catch (_: Exception) {}
            }
        }
        val compactPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mediumPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(2), dp(3), dp(3))
        }

        fun toggleSize() {
            compact = !compact
            FloatingPrefs.setCompactTimer(this, compact)
            compactPanel.visibility = if (compact) View.VISIBLE else View.GONE
            mediumPanel.visibility = if (compact) View.GONE else View.VISIBLE
            fitWindow()
        }
        val compactClock = TextView(this).apply {
            text = formatMillis(timerDuration)
            setTextColor(0xffffc653.toInt())
            textSize = 24f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        compactCountdownText = compactClock
        compactPanel.addView(compactClock, LinearLayout.LayoutParams(0, dp(42), 1f))
        val openEye = overlayButton("👁") { toggleSize() }.apply {
            textSize = 19f
            contentDescription = "Show medium timer controls"
        }
        compactPanel.addView(openEye, LinearLayout.LayoutParams(dp(37), dp(40)))

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val title = TextView(this).apply {
            text = timerLabel
            textSize = 11.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(AndroidColor.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }
        timerTitleText = title
        header.addView(title, LinearLayout.LayoutParams(0, dp(27), 1f))
        val closeEye = overlayButton("👁") { toggleSize() }.apply {
            textSize = 18f
            contentDescription = "Collapse timer to clock only"
        }
        header.addView(closeEye, LinearLayout.LayoutParams(dp(38), dp(30)))
        mediumPanel.addView(header)

        val largeClock = TextView(this).apply {
            text = formatMillis(timerDuration)
            setTextColor(0xffffc653.toInt())
            textSize = 29f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        countdownText = largeClock
        mediumPanel.addView(largeClock, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(43)
        ))
        val status = TextView(this).apply {
            setTextColor(0xfff5e7d1.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
        }
        timerStatusText = status
        mediumPanel.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(21)
        ))

        fun row(vararg buttons: AndroidButton): LinearLayout {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (button in buttons) {
                button.textSize = 10.5f
                container.addView(button, LinearLayout.LayoutParams(0, dp(31), 1f).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                    bottomMargin = dp(4)
                })
            }
            return container
        }

        val pause = overlayButton("Pause") {
            if (timerPaused) resumeTimer() else pauseTimer()
        }
        pauseButton = pause
        mediumPanel.addView(row(pause, overlayButton("Restart") { restartTimer() }))

        val editor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(3), dp(5), dp(3), dp(2))
            background = roundedBackground(0xff30271e.toInt(), 9f)
        }
        timerEditPanel = editor
        val editToggle = overlayButton("Edit + / −") {
            editor.visibility = if (editor.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            fitWindow()
        }
        mediumPanel.addView(row(
            editToggle,
            overlayButton("Stop") { stopTimerOverlay(); maybeStopService() }
        ))

        val editCaption = TextView(this).apply {
            text = "CHANGE TIME"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(0xffffcf72.toInt())
        }
        editor.addView(editCaption, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(17)
        ))
        editor.addView(row(
            overlayButton("−1 min") { adjustTimerBy(-60_000L) },
            overlayButton("+1 min") { adjustTimerBy(60_000L) }
        ))
        editor.addView(row(
            overlayButton("−10 sec") { adjustTimerBy(-10_000L) },
            overlayButton("+10 sec") { adjustTimerBy(10_000L) }
        ))
        val presetCaption = TextView(this).apply {
            text = "NEW COUNTDOWN"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(0xffffcf72.toInt())
        }
        editor.addView(presetCaption, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(17)
        ))
        editor.addView(row(
            overlayButton("1m") { setTimerPreset(60L) },
            overlayButton("5m") { setTimerPreset(300L) }
        ))
        editor.addView(row(
            overlayButton("10m") { setTimerPreset(600L) },
            overlayButton("15m") { setTimerPreset(900L) }
        ))
        val repeatAction = overlayButton("Toggle Repeat") {
            timerRepeat = !timerRepeat
            saveActiveTimerSettings()
            refreshTimerDisplay()
        }
        repeatAction.textSize = 11f
        editor.addView(repeatAction, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(32)
        ))
        timerSettingText = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(0xfff5e7d1.toInt())
            textSize = 10f
        }
        editor.addView(timerSettingText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(20)
        ))
        mediumPanel.addView(editor)
        root.addView(compactPanel)
        root.addView(mediumPanel)

        compactPanel.visibility = if (compact) View.VISIBLE else View.GONE
        mediumPanel.visibility = if (compact) View.GONE else View.VISIBLE
        // Both clock and medium title are drag handles; only the eye consumes
        // the tap used to change size. No IME, Activity or focus transfer.
        makeDraggable(compactClock, params, onMoveFinished = {
            FloatingPrefs.saveTimerPosition(this, params.x, params.y)
        }, onTap = {})
        makeDraggable(title, params, onMoveFinished = {
            FloatingPrefs.saveTimerPosition(this, params.x, params.y)
        }, onTap = {})

        timerView = root
        timerParams = params
        compactTimerPanel = compactPanel
        mediumTimerPanel = mediumPanel
        try {
            windowManager.addView(root, params)
        } catch (_: Exception) {
            timerView = null
            timerParams = null
            compactTimerPanel = null
            mediumTimerPanel = null
            countdownText = null
            compactCountdownText = null
            timerTitleText = null
            pauseButton = null
            timerStatusText = null
            timerSettingText = null
            timerEditPanel = null
        }
    }

    private fun showTimerEditor() {
        val view = timerView ?: return
        compactTimerPanel?.visibility = View.GONE
        mediumTimerPanel?.visibility = View.VISIBLE
        FloatingPrefs.setCompactTimer(this, false)
        timerEditPanel?.visibility = View.VISIBLE
        val params = timerParams ?: return
        params.width = minOf(dp(192), resources.displayMetrics.widthPixels - dp(16))
        view.post {
            if (timerView !== view) return@post
            params.x = params.x.coerceIn(
                0, (resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0)
            )
            params.y = params.y.coerceIn(
                0, (resources.displayMetrics.heightPixels - view.height - dp(20)).coerceAtLeast(0)
            )
            try {
                windowManager.updateViewLayout(view, params)
                FloatingPrefs.saveTimerPosition(this, params.x, params.y)
            } catch (_: Exception) {}
        }
    }

    private fun refreshTimerDisplay() {
        timerTitleText?.text = timerLabel.uppercase()
        countdownText?.text = formatMillis(timerRemaining)
        compactCountdownText?.text = formatMillis(timerRemaining)
        pauseButton?.text = if (timerPaused) "Resume" else "Pause"
        val state = if (timerPaused) "PAUSED" else "RUNNING"
        timerStatusText?.text = "$state   •   " + if (timerRepeat) "REPEAT ON" else "ONE TIME"
        timerSettingText?.text =
            "Repeat: " + if (timerRepeat) "ON — restarts after zero" else "OFF — alerts once"
    }

    private fun saveActiveTimerSettings() {
        FloatingPrefs.save(this, timerLabel, timerDuration / 1000L, timerRepeat)
    }

    /** Nudge time without ever opening a keyboard or changing OSRS input focus. */
    private fun adjustTimerBy(deltaMillis: Long) {
        if (timerView == null) return
        val current = if (timerPaused) timerRemaining
            else max(0L, timerEndAt - System.currentTimeMillis())
        val maxDuration = (999L * 60L + 59L) * 1000L
        val elapsed = (timerDuration - current).coerceAtLeast(0L)
        val newRemaining = (current + deltaMillis).coerceIn(
            1_000L, (maxDuration - elapsed).coerceAtLeast(1_000L)
        )
        timerDuration = (elapsed + newRemaining).coerceAtMost(maxDuration)
        timerRemaining = newRemaining
        if (!timerPaused) timerEndAt = System.currentTimeMillis() + newRemaining
        saveActiveTimerSettings()
        refreshTimerDisplay()
    }

    private fun setTimerPreset(seconds: Long) {
        timerDuration = seconds * 1000L
        timerRemaining = timerDuration
        timerEndAt = System.currentTimeMillis() + timerDuration
        timerPaused = false
        saveActiveTimerSettings()
        refreshTimerDisplay()
    }

    private fun pauseTimer() {
        if (timerView == null || timerPaused) return
        timerRemaining = max(0L, timerEndAt - System.currentTimeMillis())
        timerPaused = true
        refreshTimerDisplay()
    }

    private fun resumeTimer() {
        if (timerView == null || !timerPaused) return
        timerEndAt = System.currentTimeMillis() + timerRemaining
        timerPaused = false
        refreshTimerDisplay()
    }

    private fun restartTimer() {
        if (timerView == null) {
            startTimerFromPrefs()
            return
        }
        timerPaused = false
        timerRemaining = timerDuration
        timerEndAt = System.currentTimeMillis() + timerDuration
        refreshTimerDisplay()
    }

    private fun stopTimerOverlay() {
        handler.removeCallbacks(timerTick)
        removeViewSafely(timerView)
        timerView = null
        timerParams = null
        countdownText = null
        compactCountdownText = null
        compactTimerPanel = null
        mediumTimerPanel = null
        timerTitleText = null
        pauseButton = null
        timerStatusText = null
        timerSettingText = null
        timerEditPanel = null
        timerPaused = false
    }

    private fun signalTimer() {
        TimerNotifications.show(
            this,
            ((System.currentTimeMillis() / 1000L) and 0x7fffffff).toInt(),
            timerLabel
        )
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 180, 120, 180), -1)
        }
    }

    private fun maybeStopService() {
        if (bubbleView == null && timerView == null && puzzleView == null) stopSelf()
    }

    private fun overlayButton(label: String, click: () -> Unit): AndroidButton {
        return AndroidButton(this).apply {
            text = label
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(AndroidColor.WHITE)
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(3), dp(3), dp(3), dp(3))
            maxLines = 1
            gravity = Gravity.CENTER
            background = roundedBackground(0xff493b30.toInt(), 9f)
            setOnClickListener { click() }
        }
    }

    private fun makeDraggable(
        view: View,
        params: WindowManager.LayoutParams,
        onMoveFinished: () -> Unit,
        onTap: () -> Unit
    ) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > dp(3) || abs(dy) > dp(3)) moved = true
                    try {
                        val target: View? = if (view === bubbleView) bubbleView else timerView
                        if (target != null) {
                            params.x = (startX + dx).coerceIn(
                                0, (resources.displayMetrics.widthPixels - target.width).coerceAtLeast(0)
                            )
                            params.y = (startY + dy).coerceIn(
                                0, (resources.displayMetrics.heightPixels - target.height - dp(24)).coerceAtLeast(0)
                            )
                            windowManager.updateViewLayout(target, params)
                        }
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    onMoveFinished()
                    if (!moved) onTap()
                    true
                }
                else -> false
            }
        }
    }

    private fun formatMillis(ms: Long): String {
        val seconds = (ms + 999L) / 1000L
        val min = seconds / 60L
        val sec = seconds % 60L
        return min.toString().padStart(2, '0') + ":" + sec.toString().padStart(2, '0')
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
            setStroke(dp(1), 0xff8b6722.toInt())
        }

    private fun removeViewSafely(view: View?) {
        if (view == null) return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL,
                "Floating companion",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the OSRS floating companion controls available"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

@Composable
fun FloatingToolsScreen(nav: NavHostController) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(FloatingPrefs.name(context)) }
    val initial = FloatingPrefs.seconds(context)
    var minutes by remember { mutableStateOf((initial / 60L).toString()) }
    var seconds by remember { mutableStateOf((initial % 60L).toString()) }
    var repeat by remember { mutableStateOf(FloatingPrefs.repeat(context)) }
    var permissionRefresh by remember { mutableStateOf(0) }
    val hasPermission = remember(permissionRefresh) { Settings.canDrawOverlays(context) }

    fun selectedSeconds(): Long {
        val m = (minutes.toLongOrNull() ?: 0L).coerceIn(0L, 999L)
        val s = (seconds.toLongOrNull() ?: 0L).coerceIn(0L, 59L)
        return (m * 60L + s).coerceAtLeast(1L)
    }

    fun save() {
        FloatingPrefs.save(
            context,
            name.trim().ifBlank { "AFK check" },
            selectedSeconds(),
            repeat
        )
    }

    fun send(action: String) {
        save()
        if (!Settings.canDrawOverlays(context)) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.packageName)
                )
            )
            return
        }
        val intent = Intent(context, FloatingOverlayService::class.java).setAction(action)
        ContextCompat.startForegroundService(context, intent)
    }

    Screen(nav, "Floating Companion") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = FloatStone),
                    border = BorderStroke(1.dp, FloatGoldDark)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("NON-FOCUSING OVERLAY", color = FloatGold, fontWeight = FontWeight.Bold)
                        Text(
                            "The companion bubble and floating timer use non-focusable Android overlays. Their buttons work without making Companion the foreground app; OSRS keeps input focus.",
                            color = FloatParchment
                        )
                        Text(
                            if (hasPermission) "Overlay permission: ON" else "Overlay permission: OFF",
                            color = if (hasPermission) FloatGold else FloatText,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = {
                                permissionRefresh++
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName)
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (hasPermission) "Open overlay setting" else "Enable appear on top")
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = FloatStone),
                    border = BorderStroke(1.dp, FloatGoldDark)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Text("FLOATING TIMER", color = FloatGold, fontWeight = FontWeight.Bold)
                        Text(
                            "The eye icon switches between a tiny clock-only timer and a medium timer with Pause, Restart, Stop and a small Edit panel. No focus is taken from OSRS.",
                            color = FloatParchment
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(24) },
                            label = { Text("Timer name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = minutes,
                                onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                                label = { Text("Minutes") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = seconds,
                                onValueChange = { seconds = it.filter(Char::isDigit).take(2) },
                                label = { Text("Seconds") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Repeat timer", color = FloatText, fontWeight = FontWeight.Bold)
                                Text(
                                    if (repeat) "Restarts automatically at 0:00" else "Stops after one alert",
                                    color = FloatParchment
                                )
                            }
                            Switch(checked = repeat, onCheckedChange = { repeat = it })
                        }

                        Button(
                            onClick = { send(FloatingOverlayService.ACTION_START_TIMER) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FloatGold,
                                contentColor = Color.Black
                            )
                        ) { Text("Start floating timer") }

                        OutlinedButton(
                            onClick = { send(FloatingOverlayService.ACTION_STOP_TIMER) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Stop floating timer") }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = FloatStone),
                    border = BorderStroke(1.dp, FloatGoldDark)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("COMPANION BUBBLE", color = FloatGold, fontWeight = FontWeight.Bold)
                        Text(
                            "Draggable quick controls that stay over OSRS without making the Companion app the foreground app.",
                            color = FloatParchment
                        )
                        Button(
                            onClick = { send(FloatingOverlayService.ACTION_SHOW_COMPANION) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FloatGold,
                                contentColor = Color.Black
                            )
                        ) { Text("Show companion bubble") }
                        OutlinedButton(
                            onClick = { send(FloatingOverlayService.ACTION_HIDE_COMPANION) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Hide companion bubble") }
                        OutlinedButton(
                            onClick = { send(FloatingOverlayService.ACTION_STOP_ALL) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Stop all floating controls") }
                    }
                }
            }
        }
    }
}

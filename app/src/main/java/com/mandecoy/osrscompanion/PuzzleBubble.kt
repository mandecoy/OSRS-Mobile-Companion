package com.mandecoy.osrscompanion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import java.io.File

/**
 * The puzzle has to be recognised from an uploaded screenshot once, inside the
 * main app. Subsequent groups can be displayed by a non-focusable system
 * overlay: no Activity, screenshot picker or keyboard is launched over OSRS.
 */
object PuzzleBubbleStore {
    private const val PREFS = "puzzle_overlay_v1"
    private const val REF_FILE = "puzzle_overlay_reference.png"
    private const val PREVIEW_FILE = "puzzle_overlay_preview.png"
    @Volatile private var cached: PuzzleAnalysis? = null

    fun isVerified(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("board_verified", false)

    fun setVerified(context: Context, yes: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("board_verified", yes).apply()
    }

    @Synchronized
    fun save(context: Context, analysis: PuzzleAnalysis, start: Int = 0) {
        cached = analysis
        try {
            context.openFileOutput(REF_FILE, Context.MODE_PRIVATE).use {
                analysis.reference.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            context.openFileOutput(PREVIEW_FILE, Context.MODE_PRIVATE).use {
                analysis.preview.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("name", analysis.puzzleName)
                .putString("board", analysis.board.joinToString(","))
                .putString("moves", analysis.moves.joinToString(","))
                .putFloat("difference", analysis.matchDifference)
                .putBoolean("recognition_impossible", analysis.inferredParityRepair)
                .putInt("start", start.coerceIn(0, analysis.moves.size))
                .apply()
        } catch (_: Exception) {
            // The in-memory session is still usable while this process lives.
        }
    }

    // The editing screen may restore an unverified puzzle and its drafts;
    // only the floating overlay is allowed to call the verified load().
    fun load(context: Context): PuzzleAnalysis? =
        if (isVerified(context)) loadForEditing(context) else null

    @Synchronized
    fun loadForEditing(context: Context): PuzzleAnalysis? {
        cached?.let { return it }
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val board = prefs.getString("board", null)
                ?.split(",")?.map { it.toInt() }?.toIntArray() ?: return null
            val moves = prefs.getString("moves", null)
                ?.takeIf { it.isNotBlank() }?.split(",")?.map { it.toInt() } ?: emptyList()
            if (board.size != 25 || board.toSet().size != 25 ||
                board.any { it !in 0..24 }) return null
            val ref = BitmapFactory.decodeFile(File(context.filesDir, REF_FILE).absolutePath)
                ?: return null
            val original = BitmapFactory.decodeFile(File(context.filesDir, PREVIEW_FILE).absolutePath)
                ?: return null
            PuzzleAnalysis(
                prefs.getString("name", "Puzzle") ?: "Puzzle",
                original, ref, board, moves,
                prefs.getFloat("difference", 0f),
                prefs.getBoolean("recognition_impossible", false)
            ).also { cached = it }
        } catch (_: Exception) {
            null
        }
    }

    fun start(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("start", 0)

    fun setStart(context: Context, start: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("start", start.coerceAtLeast(0)).apply()
    }
}

/** Image-and-number overlay for a single group of five taps. */
class PuzzleBubbleBoard(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var puzzle: PuzzleAnalysis? = null
        private set
    var groupStart: Int = 0
        private set

    fun show(analysis: PuzzleAnalysis, start: Int) {
        puzzle = analysis
        groupStart = start.coerceIn(0, analysis.moves.size)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = puzzle ?: return
        if (width <= 0 || height <= 0) return
        val size = minOf(width, height).toFloat()
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val cell = size / 5f
        val art = renderPuzzleAtMove(p, groupStart)
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(art, null, RectF(left, top, left + size, top + size), paint)

        val gap = PuzzleEngine.gapPositionAt(p.board, p.moves, groupStart)
        val gapCol = gap % 5
        val gapRow = gap / 5
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * resources.displayMetrics.density
        canvas.drawRect(
            left + gapCol * cell + 2f, top + gapRow * cell + 2f,
            left + (gapCol + 1) * cell - 2f, top + (gapRow + 1) * cell - 2f, paint
        )
        paint.style = Paint.Style.FILL

        val groups = PuzzleEngine.moveHints(p.board, p.moves, groupStart, 5)
            .groupBy { it.position }
        for ((position, taps) in groups) {
            val cx = left + (position % 5 + 0.5f) * cell
            val cy = top + (position / 5 + 0.5f) * cell
            val label = taps.joinToString("/") { it.moveNumber.toString() }
            val halfWidth = if (label.length > 3) cell * 0.45f else cell * 0.34f
            paint.color = Color.rgb(225, 174, 61)
            canvas.drawRoundRect(
                cx - halfWidth, cy - cell * 0.20f,
                cx + halfWidth, cy + cell * 0.20f,
                cell * 0.09f, cell * 0.09f, paint
            )
            paint.color = Color.BLACK
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textSize = cell * if (label.length > 4) 0.22f else 0.31f
            canvas.drawText(label, cx, cy - (paint.ascent() + paint.descent()) / 2f, paint)
        }
        // Add thin cell outlines so the tap positions remain unambiguous.
        paint.color = Color.rgb(112, 79, 25)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * resources.displayMetrics.density
        for (i in 0..5) {
            canvas.drawLine(left + i * cell, top, left + i * cell, top + size, paint)
            canvas.drawLine(left, top + i * cell, left + size, top + i * cell, paint)
        }
        paint.style = Paint.Style.FILL
    }
}

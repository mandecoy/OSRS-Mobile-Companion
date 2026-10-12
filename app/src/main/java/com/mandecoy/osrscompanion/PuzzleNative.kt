package com.mandecoy.osrscompanion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Base64
import java.security.MessageDigest
import java.util.PriorityQueue
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PuzzleAnalysis(
    val puzzleName: String,
    val preview: Bitmap,
    val reference: Bitmap,
    val board: IntArray,
    val moves: List<Int>,
    val matchDifference: Float,
    /** True when recognized tiles have impossible parity and need manual correction. */
    val inferredParityRepair: Boolean = false
)

data class PuzzleMoveHint(
    val moveNumber: Int,
    val position: Int,
    val arrow: String
)

object PuzzleEngine {
    private const val SIDE = 5
    private const val CELL_COUNT = 25
    private const val GAP = 24
    private const val SAMPLE_SIDE = 7
    private const val SEARCH_LIMIT = 350_000
    private const val LEARNING_PREFS = "puzzle_visual_learning_v1"
    private const val DRAFT_PREFS = "puzzle_recognition_drafts_v1"
    private const val FEATURE_COUNT = SAMPLE_SIDE * SAMPLE_SIDE * 3

    /**
     * Persist only picture appearance by SOLVED artwork identity, not a
     * shuffled board. A verified tile appearance can be reused on new puzzles.
     * Blank tile 24 is never learned as picture artwork.
     */
    private fun learnedFeatures(context: Context, name: String): Array<FloatArray>? {
        val raw = context.getSharedPreferences(LEARNING_PREFS, Context.MODE_PRIVATE)
            .getString("artwork_$name", null) ?: return null
        return try {
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            if (bytes.size != 24 * FEATURE_COUNT) return null
            Array(24) { tile ->
                FloatArray(FEATURE_COUNT) { index ->
                    (bytes[tile * FEATURE_COUNT + index].toInt() and 255).toFloat()
                }
            }
        } catch (_: Exception) { null }
    }

    fun hasLearnedArtwork(context: Context, name: String): Boolean =
        learnedFeatures(context, name) != null

    /** Remove a mislearned template without deleting any current board edits. */
    fun forgetLearnedArtwork(context: Context, name: String) {
        context.getSharedPreferences(LEARNING_PREFS, Context.MODE_PRIVATE)
            .edit().remove("artwork_$name").apply()
    }

    /** Called ONLY after the user verifies the corrected artwork visually. */
    fun rememberVerifiedTiles(context: Context, solved: PuzzleAnalysis): Boolean {
        if (!PuzzleTileMapping.isPermutation(solved.board) ||
            !PuzzleTileMapping.isSolvable(solved.board)) return false
        return try {
            val observed = tileFeatures(solved.preview)
            val previous = learnedFeatures(context, solved.puzzleName)
            val bytes = ByteArray(24 * FEATURE_COUNT)
            for (position in 0 until CELL_COUNT) {
                val tile = solved.board[position]
                if (tile == GAP) continue
                for (i in 0 until FEATURE_COUNT) {
                    // Blend with a previous confirmed photo to handle camera
                    // rescaling and compression rather than overwriting it.
                    val current = observed[position][i]
                    val mean = if (previous == null) current
                               else (current * 0.65f + previous[tile][i] * 0.35f)
                    bytes[tile * FEATURE_COUNT + i] =
                        mean.toInt().coerceIn(0, 255).toByte()
                }
            }
            context.getSharedPreferences(LEARNING_PREFS, Context.MODE_PRIVATE)
                .edit().putString("artwork_${solved.puzzleName}",
                    Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
            true
        } catch (_: Exception) { false }
    }

    /** Signature of the CROPPED screenshot; never use a prior shuffled board
     * for a screenshot with different pixels or another official artwork. */
    private fun croppedFingerprint(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val pixels = IntArray(32 * 32)
        scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        val md = MessageDigest.getInstance("SHA-256")
        for (px in pixels) {
            md.update((px ushr 16).toByte())
            md.update((px ushr 8).toByte())
            md.update(px.toByte())
        }
        if (scaled !== bitmap) scaled.recycle()
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun saveWorkingBoard(context: Context, original: PuzzleAnalysis, board: IntArray) {
        if (!PuzzleTileMapping.isPermutation(board)) return
        try {
            val key = "${original.puzzleName}:${croppedFingerprint(original.preview)}"
            context.getSharedPreferences(DRAFT_PREFS, Context.MODE_PRIVATE).edit()
                .putString("key", key)
                .putString("board", board.joinToString(","))
                .apply()
        } catch (_: Exception) {}
    }

    fun savedBoardForScreenshot(context: Context, original: PuzzleAnalysis): IntArray? {
        return try {
            val prefs = context.getSharedPreferences(DRAFT_PREFS, Context.MODE_PRIVATE)
            val key = "${original.puzzleName}:${croppedFingerprint(original.preview)}"
            if (prefs.getString("key", "") != key) return null
            val board = prefs.getString("board", null)?.split(",")
                ?.map { it.toInt() }?.toIntArray() ?: return null
            board.takeIf { PuzzleTileMapping.isPermutation(it) }
        } catch (_: Exception) { null }
    }

    /** Same screenshot only; use corrected tiles if already valid. */
    fun restoreBoardForScreenshot(context: Context, original: PuzzleAnalysis): PuzzleAnalysis {
        val stored = savedBoardForScreenshot(context, original) ?: return original
        if (!PuzzleTileMapping.isSolvable(stored)) return original
        return solveCorrectedBoard(original, stored) ?: original
    }


    private val referenceFiles = listOf(
        "Castle" to "Castle_puzzle_solved.png",
        "Tree" to "Tree_puzzle_solved.png",
        "Troll" to "Troll_puzzle_solved.png",
        "Zulrah" to "Zulrah_puzzle_solved.png",
        "Cerberus" to "Cerberus_puzzle_solved.png",
        "Gnome child" to "Gnome_child_puzzle_solved.png",
        "Theatre of Blood" to "Theatre_of_Blood_puzzle_solved.png"
    )

    private data class Ref(
        val name: String,
        val grid: Bitmap,
        val tileFeatures: Array<FloatArray>
    )
    private var cachedReferences: List<Ref>? = null

    fun analyse(context: Context, screenshot: Bitmap): PuzzleAnalysis? {
        val tileRect = locatePuzzleGrid(screenshot) ?: return null
        val cropped = Bitmap.createBitmap(
            screenshot,
            tileRect.left,
            tileRect.top,
            tileRect.width(),
            tileRect.height()
        )
        val refs = references(context)
        if (refs.isEmpty()) return null

        val observed = tileFeatures(cropped)
        var winner: Ref? = null
        var winnerAssignment: IntArray? = null
        var winnerCosts: Array<DoubleArray>? = null
        var winnerMean = Float.MAX_VALUE

        for (ref in refs) {
            val learned = learnedFeatures(context, ref.name)
            val costs = Array(CELL_COUNT) { pos ->
                DoubleArray(CELL_COUNT) { tile ->
                    val original = featureDistance(observed[pos], ref.tileFeatures[tile])
                    if (learned != null && tile != GAP) {
                        val confirmed = featureDistance(observed[pos], learned[tile])
                        // Trust a previously verified picture but retain an
                        // independent official-reference check.
                        (original * 0.30f + confirmed * 0.70f).toDouble()
                    } else original.toDouble()
                }
            }
            val assignment = minimumAssignmentWithForcedGap(costs, detectEmptyPosition(cropped))
            var total = 0.0
            for (pos in 0 until CELL_COUNT) total += costs[pos][assignment[pos]]
            val mean = (total / CELL_COUNT).toFloat()
            if (mean < winnerMean) {
                winner = ref
                winnerAssignment = assignment
                winnerCosts = costs
                winnerMean = mean
            }
        }

        val ref = winner ?: return null
        val board = winnerAssignment ?: return null
        val costs = winnerCosts ?: return null

        // RuneLite receives exact tile IDs from the game client; this app
        // must infer IDs from an image. Never alter the detected tile labels
        // silently to force solvability: that can produce a perfectly legal
        // solution for a board that does not exist in OSRS.
        val recognitionImpossible = !solvable(board)
        val moves = if (recognitionImpossible) emptyList()
                    else findSolution(board) ?: emptyList()
        return PuzzleAnalysis(
            puzzleName = ref.name,
            preview = cropped,
            reference = ref.grid,
            board = board.copyOf(),
            moves = moves,
            matchDifference = winnerMean,
            inferredParityRepair = recognitionImpossible
        )
    }


    /**
     * Let the player correct a wrongly detected empty square without uploading again.
     * Reassign all 24 picture tiles against the same official artwork; simply
     * moving the GAP marker on an existing assignment would corrupt the board.
     */
    fun correctEmptyPosition(current: PuzzleAnalysis, gapPosition: Int): PuzzleAnalysis? {
        if (gapPosition !in 0 until CELL_COUNT) return null
        val observed = tileFeatures(current.preview)
        val referenceFeatures = tileFeatures(current.reference)
        val costs = Array(CELL_COUNT) { pos ->
            DoubleArray(CELL_COUNT) { tile ->
                featureDistance(observed[pos], referenceFeatures[tile]).toDouble()
            }
        }
        val assignment = minimumAssignmentWithForcedGap(costs, gapPosition)
        val recognitionImpossible = !solvable(assignment)
        val solution = if (recognitionImpossible) emptyList()
                       else findSolution(assignment) ?: emptyList()
        return current.copy(
            board = assignment, moves = solution,
            inferredParityRepair = recognitionImpossible
        )
    }

    /**
     * User-confirmed picture tile labels. These are the zero-based SOLVED
     * destinations for each visible tile in the original screenshot.
     * Reject invalid / unsolvable arrangements rather than inventing a
     * move sequence that solves a different puzzle.
     */
    fun solveCorrectedBoard(current: PuzzleAnalysis, verifiedBoard: IntArray): PuzzleAnalysis? {
        if (verifiedBoard.size != CELL_COUNT ||
            verifiedBoard.any { it !in 0 until CELL_COUNT } ||
            verifiedBoard.toSet().size != CELL_COUNT ||
            !solvable(verifiedBoard)) return null
        val solution = findSolution(verifiedBoard) ?: return null
        return current.copy(
            board = verifiedBoard.copyOf(),
            moves = solution,
            inferredParityRepair = false
        )
    }

    /**
     * OSRS blank cells have a nearly uniform dark interior. Use that as an
     * independent signal instead of treating the solved picture's last square
     * as if it were necessarily an empty tile. Only commit when unambiguous.
     */
    private fun detectEmptyPosition(image: Bitmap): Int? {
        val w = image.width / SIDE.toFloat()
        val h = image.height / SIDE.toFloat()
        if (w < 10f || h < 10f) return null
        val scores = ArrayList<Pair<Int, Double>>(CELL_COUNT)
        for (pos in 0 until CELL_COUNT) {
            val col = pos % SIDE
            val row = pos / SIDE
            var total = 0.0
            var squared = 0.0
            var count = 0
            for (sy in 0 until 7) for (sx in 0 until 7) {
                val x = ((col + 0.28f + sx * 0.44f / 6f) * w).toInt().coerceIn(0, image.width - 1)
                val y = ((row + 0.28f + sy * 0.44f / 6f) * h).toInt().coerceIn(0, image.height - 1)
                val c = image.getPixel(x, y)
                val luma = luminance(c).toDouble()
                total += luma
                squared += luma * luma
                count++
            }
            val mean = total / count
            val deviation = kotlin.math.sqrt(max(0.0, squared / count - mean * mean))
            scores.add(pos to (mean + deviation * 1.5))
        }
        scores.sortBy { it.second }
        if (scores.size < 2) return null
        // Avoid guessing when a dark patch in the artwork resembles a blank.
        return if (scores[0].second < 120.0 && scores[1].second - scores[0].second > 12.0) {
            scores[0].first
        } else null
    }

    fun moveHints(board: IntArray, moves: List<Int>, start: Int, count: Int = 5): List<PuzzleMoveHint> {
        if (start >= moves.size) return emptyList()
        val state = board.copyOf()
        var gap = state.indexOf(GAP)

        for (i in 0 until min(start, moves.size)) {
            val next = moves[i]
            state[gap] = state[next]
            state[next] = GAP
            gap = next
        }

        val hints = ArrayList<PuzzleMoveHint>()
        val end = min(moves.size, start + count)
        for (i in start until end) {
            val tilePos = moves[i]
            val arrow = when {
                tilePos == gap - SIDE -> "↑"
                tilePos == gap + SIDE -> "↓"
                tilePos == gap - 1 -> "←"
                tilePos == gap + 1 -> "→"
                else -> "•"
            }
            hints.add(PuzzleMoveHint(i + 1, tilePos, arrow))
            state[gap] = state[tilePos]
            state[tilePos] = GAP
            gap = tilePos
        }
        return hints
    }

    fun gapPositionAt(board: IntArray, moves: List<Int>, start: Int): Int {
        val state = board.copyOf()
        var gap = state.indexOf(GAP)
        for (i in 0 until min(start, moves.size)) {
            val next = moves[i]
            state[gap] = state[next]
            state[next] = GAP
            gap = next
        }
        return gap
    }

    fun instructions(board: IntArray, moves: List<Int>, start: Int, count: Int = 5): List<String> {
        if (start >= moves.size) return emptyList()
        val state = board.copyOf()
        var gap = state.indexOf(GAP)
        for (i in 0 until min(start, moves.size)) {
            val next = moves[i]
            state[gap] = state[next]
            state[next] = GAP
            gap = next
        }

        val answer = ArrayList<String>()
        val end = min(moves.size, start + count)
        for (i in start until end) {
            val tilePos = moves[i]
            val arrow = when {
                tilePos == gap - SIDE -> "↑"
                tilePos == gap + SIDE -> "↓"
                tilePos == gap - 1 -> "←"
                tilePos == gap + 1 -> "→"
                else -> "•"
            }
            answer.add(arrow)
            state[gap] = state[tilePos]
            state[tilePos] = GAP
            gap = tilePos
        }
        return answer
    }

    private fun references(context: Context): List<Ref> {
        cachedReferences?.let { return it }
        val loaded = ArrayList<Ref>()
        for ((name, file) in referenceFiles) {
            val bitmap = try {
                context.assets.open("puzzles/$file").use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                try {
                    val connection = (URL("https://oldschool.runescape.wiki/w/Special:Redirect/file/" + file)
                        .openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 10_000
                        readTimeout = 15_000
                        setRequestProperty("User-Agent", "OSRS-Companion-Tool/0.5.5")
                    }
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } catch (_: Exception) {
                    null
                }
            } ?: continue
            val area = refineGrid(bitmap, Rect(0, 0, bitmap.width, bitmap.height))
                ?: Rect(0, 0, bitmap.width, bitmap.height)
            val grid = Bitmap.createBitmap(bitmap, area.left, area.top, area.width(), area.height())
            loaded.add(Ref(name, grid, tileFeatures(grid)))
        }
        cachedReferences = loaded
        return loaded
    }

    private fun woodLike(color: Int): Boolean {
        val r = (color ushr 16) and 255
        val g = (color ushr 8) and 255
        val b = color and 255
        return b < 28 && r in 28..108 && g in 12..74 && r * 10 >= g * 13
    }

    private fun locatePuzzleGrid(image: Bitmap): Rect? {
        val sw = max(1, image.width / 2)
        val sh = max(1, image.height / 2)
        val base = BooleanArray(sw * sh)

        for (y in 0 until sh) {
            val sy = min(image.height - 1, y * 2)
            for (x in 0 until sw) {
                val sx = min(image.width - 1, x * 2)
                base[y * sw + x] = woodLike(image.getPixel(sx, sy))
            }
        }

        // Close small JPEG/compression gaps by expanding each matching sample a little.
        val expanded = BooleanArray(base.size)
        for (y in 0 until sh) for (x in 0 until sw) {
            if (!base[y * sw + x]) continue
            for (dy in -2..2) {
                val yy = y + dy
                if (yy !in 0 until sh) continue
                for (dx in -2..2) {
                    val xx = x + dx
                    if (xx in 0 until sw) expanded[yy * sw + xx] = true
                }
            }
        }

        val seen = BooleanArray(expanded.size)
        val queue = IntArray(expanded.size)
        var best: Rect? = null
        var bestScore = -1L
        val minScreen = min(image.width, image.height)

        for (start in expanded.indices) {
            if (!expanded[start] || seen[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            var minX = start % sw
            var maxX = minX
            var minY = start / sw
            var maxY = minY

            while (head < tail) {
                val p = queue[head++]
                val x = p % sw
                val y = p / sw
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)

                val left = if (x > 0) p - 1 else -1
                val right = if (x + 1 < sw) p + 1 else -1
                val up = if (y > 0) p - sw else -1
                val down = if (y + 1 < sh) p + sw else -1
                for (q in intArrayOf(left, right, up, down)) {
                    if (q >= 0 && expanded[q] && !seen[q]) {
                        seen[q] = true
                        queue[tail++] = q
                    }
                }
            }

            val bw = (maxX - minX + 1) * 2
            val bh = (maxY - minY + 1) * 2
            if (bw < minScreen * 0.18f || bh < minScreen * 0.18f) continue
            if (bw > minScreen * 0.82f || bh > minScreen * 0.82f) continue
            val ratio = bw.toFloat() / bh.toFloat()
            if (ratio !in 0.78f..1.28f) continue

            val score = tail.toLong() * min(bw, bh)
            if (score > bestScore) {
                bestScore = score
                best = Rect(
                    max(0, minX * 2 - 6),
                    max(0, minY * 2 - 6),
                    min(image.width, (maxX + 1) * 2 + 6),
                    min(image.height, (maxY + 1) * 2 + 6)
                )
            }
        }

        return best?.let { refineGrid(image, it) }
    }

    private fun refineGrid(image: Bitmap, rough: Rect): Rect? {
        if (rough.width() < 80 || rough.height() < 80) return null

        val w = rough.width()
        val h = rough.height()
        val vertical = DoubleArray(w)
        val horizontal = DoubleArray(h)

        for (x in 1 until w) {
            var sum = 0.0
            val xx = rough.left + x
            for (y in rough.top until rough.bottom) {
                val a = luminance(image.getPixel(xx, y))
                val b = luminance(image.getPixel(xx - 1, y))
                sum += abs(a - b)
            }
            vertical[x] = sum
        }

        for (y in 1 until h) {
            var sum = 0.0
            val yy = rough.top + y
            for (x in rough.left until rough.right) {
                val a = luminance(image.getPixel(x, yy))
                val b = luminance(image.getPixel(x, yy - 1))
                sum += abs(a - b)
            }
            horizontal[y] = sum
        }

        val xs = sixLineSequence(vertical, w) ?: return null
        val ys = sixLineSequence(horizontal, h) ?: return null
        val cellX = (xs.last() - xs.first()) / 5f
        val cellY = (ys.last() - ys.first()) / 5f
        if (cellX <= 5f || cellY <= 5f) return null
        if (max(cellX, cellY) / min(cellX, cellY) > 1.18f) return null

        val left = (rough.left + xs.first()).coerceIn(0, image.width - 1)
        val top = (rough.top + ys.first()).coerceIn(0, image.height - 1)
        val right = (rough.left + xs.last()).coerceIn(left + 5, image.width)
        val bottom = (rough.top + ys.last()).coerceIn(top + 5, image.height)
        return Rect(left, top, right, bottom)
    }

    private fun sixLineSequence(score: DoubleArray, extent: Int): IntArray? {
        val minStep = max(8, extent / 8)
        val maxStep = max(minStep, extent / 4)
        var bestValue = Double.NEGATIVE_INFINITY
        var best: IntArray? = null

        for (step in minStep..maxStep) {
            val maxStart = extent - step * 5 - 1
            if (maxStart <= 1) continue
            val radius = max(2, step / 18)
            for (start in 1..maxStart) {
                val picks = IntArray(6)
                var value = 0.0
                var deviation = 0
                for (k in 0..5) {
                    val target = start + k * step
                    var chosen = target
                    var local = Double.NEGATIVE_INFINITY
                    val from = max(1, target - radius)
                    val to = min(extent - 1, target + radius)
                    for (p in from..to) {
                        if (score[p] > local) {
                            local = score[p]
                            chosen = p
                        }
                    }
                    picks[k] = chosen
                    value += local
                    deviation += abs(chosen - target)
                }
                value -= deviation * 250.0
                if (value > bestValue) {
                    bestValue = value
                    best = picks
                }
            }
        }
        return best
    }

    private fun luminance(c: Int): Int {
        val r = (c ushr 16) and 255
        val g = (c ushr 8) and 255
        val b = c and 255
        return (r * 3 + g * 6 + b) / 10
    }

    private fun tileFeatures(image: Bitmap): Array<FloatArray> {
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val cellW = image.width / SIDE.toFloat()
        val cellH = image.height / SIDE.toFloat()
        val keep = 0.68f

        return Array(CELL_COUNT) { tile ->
            val col = tile % SIDE
            val row = tile / SIDE
            val x0 = col * cellW + cellW * (1f - keep) / 2f
            val y0 = row * cellH + cellH * (1f - keep) / 2f
            val spanX = cellW * keep
            val spanY = cellH * keep
            val out = FloatArray(SAMPLE_SIDE * SAMPLE_SIDE * 3)

            for (sy in 0 until SAMPLE_SIDE) for (sx in 0 until SAMPLE_SIDE) {
                val ax0 = (x0 + spanX * sx / SAMPLE_SIDE).toInt().coerceIn(0, image.width - 1)
                val ay0 = (y0 + spanY * sy / SAMPLE_SIDE).toInt().coerceIn(0, image.height - 1)
                val ax1 = max(ax0 + 1, (x0 + spanX * (sx + 1) / SAMPLE_SIDE).toInt()).coerceAtMost(image.width)
                val ay1 = max(ay0 + 1, (y0 + spanY * (sy + 1) / SAMPLE_SIDE).toInt()).coerceAtMost(image.height)

                var rr = 0L; var gg = 0L; var bb = 0L; var n = 0
                for (y in ay0 until ay1) for (x in ax0 until ax1) {
                    val c = pixels[y * image.width + x]
                    rr += (c ushr 16) and 255
                    gg += (c ushr 8) and 255
                    bb += c and 255
                    n++
                }
                val index = (sy * SAMPLE_SIDE + sx) * 3
                val d = max(1, n).toFloat()
                out[index] = rr / d
                out[index + 1] = gg / d
                out[index + 2] = bb / d
            }
            out
        }
    }

    private fun featureDistance(a: FloatArray, b: FloatArray): Float {
        var total = 0f
        for (i in a.indices) total += abs(a[i] - b[i])
        return total / a.size
    }

    private fun minimumAssignmentWithForcedGap(cost: Array<DoubleArray>, knownGap: Int? = null): IntArray {
        // The solved reference always has the empty slot at tile 24. Matching the
        // gap separately prevents a dark artwork tile from stealing the GAP label.
        var gapPosition = 0
        var gapCost = Double.POSITIVE_INFINITY
        for (position in 0 until CELL_COUNT) {
            if (cost[position][GAP] < gapCost) {
                gapCost = cost[position][GAP]
                gapPosition = position
            }
        }

        gapPosition = knownGap?.takeIf { it in 0 until CELL_COUNT } ?: gapPosition
        val positions = (0 until CELL_COUNT).filter { it != gapPosition }
        val tiles = (0 until CELL_COUNT).filter { it != GAP }
        val reduced = Array(CELL_COUNT - 1) { row ->
            DoubleArray(CELL_COUNT - 1) { col ->
                cost[positions[row]][tiles[col]]
            }
        }

        val reducedAssignment = minimumAssignment(reduced)
        val result = IntArray(CELL_COUNT)
        result[gapPosition] = GAP
        for (row in reducedAssignment.indices) {
            result[positions[row]] = tiles[reducedAssignment[row]]
        }
        return result
    }

    private fun minimumAssignment(cost: Array<DoubleArray>): IntArray {
        val n = cost.size
        val rowPotential = DoubleArray(n + 1)
        val colPotential = DoubleArray(n + 1)
        val matchedRow = IntArray(n + 1)
        val previousCol = IntArray(n + 1)

        for (row in 1..n) {
            matchedRow[0] = row
            var col0 = 0
            val best = DoubleArray(n + 1) { Double.POSITIVE_INFINITY }
            val used = BooleanArray(n + 1)

            do {
                used[col0] = true
                val activeRow = matchedRow[col0]
                var delta = Double.POSITIVE_INFINITY
                var nextCol = 0

                for (col in 1..n) if (!used[col]) {
                    val reduced = cost[activeRow - 1][col - 1] - rowPotential[activeRow] - colPotential[col]
                    if (reduced < best[col]) {
                        best[col] = reduced
                        previousCol[col] = col0
                    }
                    if (best[col] < delta) {
                        delta = best[col]
                        nextCol = col
                    }
                }

                for (col in 0..n) {
                    if (used[col]) {
                        rowPotential[matchedRow[col]] += delta
                        colPotential[col] -= delta
                    } else {
                        best[col] -= delta
                    }
                }
                col0 = nextCol
            } while (matchedRow[col0] != 0)

            do {
                val before = previousCol[col0]
                matchedRow[col0] = matchedRow[before]
                col0 = before
            } while (col0 != 0)
        }

        val result = IntArray(n)
        for (col in 1..n) result[matchedRow[col] - 1] = col - 1
        return result
    }

    private fun solvable(board: IntArray): Boolean {
        var inversions = 0
        for (i in board.indices) {
            if (board[i] == GAP) continue
            for (j in i + 1 until board.size) {
                if (board[j] != GAP && board[j] < board[i]) inversions++
            }
        }
        return inversions % 2 == 0
    }

    private fun leastExpensiveParityFix(board: IntArray, costs: Array<DoubleArray>): Pair<Int, Int>? {
        var answer: Pair<Int, Int>? = null
        var increase = Double.POSITIVE_INFINITY
        for (a in 0 until CELL_COUNT) {
            if (board[a] == GAP) continue
            for (b in a + 1 until CELL_COUNT) {
                if (board[b] == GAP) continue
                val before = costs[a][board[a]] + costs[b][board[b]]
                val after = costs[a][board[b]] + costs[b][board[a]]
                if (after - before < increase) {
                    increase = after - before
                    answer = a to b
                }
            }
        }
        return answer
    }

    private data class Packed(val first: Long, val second: Long)

    private data class SearchNode(
        val key: Packed,
        val gap: Int,
        val depth: Int,
        val priority: Double,
        val parent: SearchNode?,
        val tappedPosition: Int
    )

    private val adjacent = Array(CELL_COUNT) { p ->
        val r = p / SIDE
        val c = p % SIDE
        val list = ArrayList<Int>(4)
        if (r > 0) list.add(p - SIDE)
        if (r + 1 < SIDE) list.add(p + SIDE)
        if (c > 0) list.add(p - 1)
        if (c + 1 < SIDE) list.add(p + 1)
        list.toIntArray()
    }

    private fun findSolution(start: IntArray): List<Int>? {
        if (start.indices.all { start[it] == it }) return emptyList()

        // Keep the proven row/column solver as a reliable fallback, but don't
        // automatically show its much longer path. A direct weighted A* search
        // can find dramatically shorter routes on partly completed puzzles.
        var best = validateAndShorten(start, FastPuzzleSolver.solve(start)?.toList())
        val lowerBound = heuristic(start)
        if (lowerBound <= 65 || best == null || best.size > 120) {
            val weights = if (lowerBound <= 48) doubleArrayOf(1.4, 1.8, 2.3)
                          else doubleArrayOf(1.8, 2.3)
            for (weight in weights) {
                val result = validateAndShorten(start, weightedSearch(start, weight, 120_000))
                if (result != null && (best == null || result.size < best.size)) best = result
                if (best != null && best.size <= lowerBound + max(8, lowerBound / 3)) break
            }
        }
        if (best != null) return best

        // Limited fallback for unusual boards. Every returned move is replayed
        // and checked before we ever show it to the player.
        for (weight in doubleArrayOf(2.0, 3.0, 4.5)) {
            validateAndShorten(start, weightedSearch(start, weight, SEARCH_LIMIT))?.let { return it }
        }
        return null
    }

    private fun validateAndShorten(start: IntArray, path: List<Int>?): List<Int>? {
        if (path == null) return null
        val board = start.copyOf()
        var gap = board.indexOf(GAP)
        if (gap < 0) return null
        val simplified = ArrayList<Int>(path.size)
        val histories = ArrayList<Packed>(path.size + 1)
        val stateIndex = HashMap<Packed, Int>()
        val initial = pack(board)
        histories.add(initial)
        stateIndex[initial] = 0
        for (move in path) {
            if (move !in adjacent[gap]) return null
            board[gap] = board[move]
            board[move] = GAP
            gap = move
            val key = pack(board)
            val seenAt = stateIndex[key]
            if (seenAt != null) {
                // This loop brought us to an identical full board state.
                // Remove all the wasted swaps and keep the current state.
                while (simplified.size > seenAt) {
                    simplified.removeAt(simplified.lastIndex)
                    stateIndex.remove(histories.removeAt(histories.lastIndex))
                }
            } else {
                simplified.add(move)
                histories.add(key)
                stateIndex[key] = simplified.size
            }
        }
        if (board.indices.any { board[it] != it }) return null
        return simplified
    }

    private fun weightedSearch(start: IntArray, weight: Double, maxExpanded: Int): List<Int>? {
        val goal = pack(IntArray(CELL_COUNT) { it })
        val queue = PriorityQueue<SearchNode> { a, b -> a.priority.compareTo(b.priority) }
        val seen = HashSet<Packed>(SEARCH_LIMIT)
        val startKey = pack(start)
        queue.add(
            SearchNode(
                startKey,
                start.indexOf(GAP),
                0,
                heuristic(start) * weight,
                null,
                -1
            )
        )

        val board = IntArray(CELL_COUNT)
        var expanded = 0

        while (queue.isNotEmpty() && expanded < maxExpanded) {
            val node = queue.poll()
            if (node.key == goal) {
                val reversed = ArrayList<Int>()
                var cursor: SearchNode? = node
                while (cursor?.parent != null) {
                    reversed.add(cursor.tappedPosition)
                    cursor = cursor.parent
                }
                reversed.reverse()
                return reversed
            }
            if (!seen.add(node.key)) continue
            expanded++

            unpack(node.key, board)
            for (next in adjacent[node.gap]) {
                if (node.parent != null && next == node.parent.gap) continue
                val candidate = board.copyOf()
                candidate[node.gap] = candidate[next]
                candidate[next] = GAP
                val key = pack(candidate)
                if (key in seen) continue
                val depth = node.depth + 1
                val estimate = heuristic(candidate)
                queue.add(
                    SearchNode(
                        key,
                        next,
                        depth,
                        depth + weight * estimate,
                        node,
                        next
                    )
                )
            }
        }
        return null
    }

    private fun heuristic(board: IntArray): Int {
        var estimate = 0
        for (pos in 0 until CELL_COUNT) {
            val tile = board[pos]
            if (tile == GAP) continue
            estimate += abs(pos / SIDE - tile / SIDE) + abs(pos % SIDE - tile % SIDE)
        }

        // Linear conflicts: two home-row/home-column tiles reversed relative to each other.
        for (row in 0 until SIDE) {
            for (a in 0 until SIDE) {
                val ta = board[row * SIDE + a]
                if (ta == GAP || ta / SIDE != row) continue
                for (b in a + 1 until SIDE) {
                    val tb = board[row * SIDE + b]
                    if (tb != GAP && tb / SIDE == row && ta > tb) estimate += 2
                }
            }
        }
        for (col in 0 until SIDE) {
            for (a in 0 until SIDE) {
                val ta = board[a * SIDE + col]
                if (ta == GAP || ta % SIDE != col) continue
                for (b in a + 1 until SIDE) {
                    val tb = board[b * SIDE + col]
                    if (tb != GAP && tb % SIDE == col && ta > tb) estimate += 2
                }
            }
        }
        return estimate
    }

    private fun pack(board: IntArray): Packed {
        var first = 0L
        var second = 0L
        for (i in 0 until 12) first = (first shl 5) or board[i].toLong()
        for (i in 12 until 24) second = (second shl 5) or board[i].toLong()
        return Packed(first, second)
    }

    private fun unpack(key: Packed, out: IntArray) {
        var first = key.first
        var second = key.second
        for (i in 11 downTo 0) {
            out[i] = (first and 31L).toInt()
            first = first ushr 5
        }
        for (i in 23 downTo 12) {
            out[i] = (second and 31L).toInt()
            second = second ushr 5
        }

        val expectedSum = CELL_COUNT * (CELL_COUNT - 1) / 2
        var partial = 0
        for (i in 0 until 24) partial += out[i]
        out[24] = expectedSum - partial
    }
}

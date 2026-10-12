package com.mandecoy.osrscompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OverlayGold = Color(0xffe0ae3d)
private val OverlayDark = Color(0xff171411)
private val OverlayParchment = Color(0xfff4ead4)

@Composable
fun AnnotatedPuzzlePreview(
    solved: PuzzleAnalysis,
    start: Int
) {
    // The board represents the state BEFORE this batch. All numbered markers
    // are screen-cell coordinates to tap IN ORDER; each subsequent OSRS tap
    // moves the artwork and gap, but not the marker's screen coordinate.
    val hints = PuzzleEngine.moveHints(solved.board, solved.moves, start, 5)
    val hintsByPosition = remember(solved, start) { hints.groupBy { it.position } }
    val gap = PuzzleEngine.gapPositionAt(solved.board, solved.moves, start)
    val previewAtStart = remember(solved, start) { renderPuzzleAtMove(solved, start) }

    Card(
        colors = CardDefaults.cardColors(containerColor = OverlayDark),
        border = BorderStroke(2.dp, Color(0xff8b6722))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp)
        ) {
            Image(
                bitmap = previewAtStart.asImageBitmap(),
                contentDescription = "Puzzle with all five next moves numbered in tap order",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0 until 5) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (col in 0 until 5) {
                            val position = row * 5 + col
                            val cellHints = hintsByPosition[position].orEmpty()

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .then(
                                        if (position == gap) {
                                            Modifier.border(
                                                2.dp,
                                                OverlayParchment,
                                                RoundedCornerShape(4.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (position == gap) {
                                    Text(
                                        "EMPTY",
                                        color = OverlayParchment,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(
                                                Color.Black.copy(alpha = 0.62f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 3.dp, vertical = 2.dp)
                                    )
                                }

                                if (cellHints.isNotEmpty()) {
                                    // A position may be tapped twice in a group.
                                    // Show BOTH step numbers instead of losing one.
                                    Column(
                                        modifier = Modifier
                                            .background(
                                                OverlayGold.copy(alpha = 0.96f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                1.dp, Color.Black.copy(alpha = 0.8f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        cellHints.forEach { hint ->
                                            Text(
                                                hint.moveNumber.toString(),
                                                color = Color.Black,
                                                fontSize = if (cellHints.size > 2) 10.sp
                                                           else if (cellHints.size > 1) 13.sp
                                                           else 19.sp,
                                                lineHeight = if (cellHints.size > 1) 14.sp else 22.sp,
                                                fontWeight = FontWeight.Black,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


internal fun renderPuzzleAtMove(solved: PuzzleAnalysis, moveCount: Int): Bitmap {
    val side = 5
    val gapTile = 24
    val state = solved.board.copyOf()
    var gap = state.indexOf(gapTile)

    val appliedMoves = moveCount.coerceAtMost(solved.moves.size)
    for (i in 0 until appliedMoves) {
        val tapped = solved.moves[i]
        state[gap] = state[tapped]
        state[tapped] = gapTile
        gap = tapped
    }

    // Picture tiles come from the official solved artwork. The GAP is rendered
    // using the actual blank tile from the player's screenshot, never the
    // picture's bottom-right cell (which might contain artwork).
    val source = solved.reference
    val originalGap = solved.board.indexOf(gapTile)
    val realBlank = solved.preview
    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    for (destination in 0 until 25) {
        val tile = state[destination]
        val sourcePosition = tile

        val srcCol = sourcePosition % side
        val srcRow = sourcePosition / side
        val dstCol = destination % side
        val dstRow = destination / side

        val src = Rect(
            srcCol * source.width / side,
            srcRow * source.height / side,
            (srcCol + 1) * source.width / side,
            (srcRow + 1) * source.height / side
        )
        val dst = Rect(
            dstCol * source.width / side,
            dstRow * source.height / side,
            (dstCol + 1) * source.width / side,
            (dstRow + 1) * source.height / side
        )
        if (tile == gapTile && originalGap >= 0) {
            val blankCol = originalGap % side
            val blankRow = originalGap / side
            val blankSrc = Rect(
                blankCol * realBlank.width / side,
                blankRow * realBlank.height / side,
                (blankCol + 1) * realBlank.width / side,
                (blankRow + 1) * realBlank.height / side
            )
            canvas.drawBitmap(realBlank, blankSrc, dst, null)
        } else {
            canvas.drawBitmap(source, src, dst, null)
        }
    }

    return output
}

/**
 * Show the real screenshot alongside the board our recognizer THINKS it read.
 * The difference is essential: a valid solution to misidentified tiles is
 * still wrong in the real game.
 */
@Composable
fun PuzzleRecognitionComparison(solved: PuzzleAnalysis) {
    val predicted = remember(solved) { renderPuzzleAtMove(solved, 0) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("REAL SCREENSHOT", color = OverlayParchment, fontSize = 14.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Image(
            bitmap = solved.preview.asImageBitmap(),
            contentDescription = "Full-size original OSRS puzzle screenshot",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                .border(2.dp, OverlayGold)
        )
        Text("APP RECOGNITION — EVERY TILE MUST MATCH", color = OverlayParchment,
            fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth())
        Image(
            bitmap = predicted.asImageBitmap(),
            contentDescription = "Full-size screenshot reconstruction from recognized tiles",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                .border(2.dp, OverlayGold)
        )
    }
}

/**
 * Visual-only tile correction. The existing numerical tile-ID guesses must NOT
 * appear here: when recognition is wrong those labels are actively misleading.
 *
 * A tap on the actual screenshot selects its 5x5 cell; a tap on the reference
 * image assigns that cell the correct solved artwork position. The chosen label
 * swaps with its former owner so the board remains a complete permutation.
 * Tapping a second screenshot cell instead swaps two current assignments.
 */
@Composable
fun PuzzleTileAssignmentPicker(
    screenshot: Bitmap,
    reference: Bitmap,
    board: IntArray,
    selectedPosition: Int,
    onSelectPosition: (Int) -> Unit,
    onChooseTile: (Int) -> Unit,
    onSwapPosition: (Int) -> Unit
) {
    var showCellFallback by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    val selectSquare: (Int) -> Unit = { position ->
        if (selectedPosition == -1 || selectedPosition == position) {
            onSelectPosition(if (selectedPosition == position) -1 else position)
        } else {
            onSwapPosition(position)
        }
    }

    Text(
        "STEP 1 — SELECT A SQUARE IN YOUR REAL PUZZLE",
        color = OverlayGold, fontSize = 13.sp, fontWeight = FontWeight.Bold
    )
    Text(
        "Tap the square whose picture the app identified incorrectly. Tile numbers are intentionally hidden because they may be wrong.",
        color = OverlayParchment, fontSize = 12.sp
    )
    SelectablePuzzlePicture(
        bitmap = screenshot,
        selectedPosition = selectedPosition,
        onPress = selectSquare
    )
    Text(
        if (selectedPosition in 0..24)
            "Selected: row ${selectedPosition / 5 + 1}, column ${selectedPosition % 5 + 1}. Now tap the MATCHING artwork below, or tap another square above to swap their guessed identities."
        else "No square selected yet. Tap a square in the photo above.",
        color = OverlayGold, fontWeight = FontWeight.Bold, fontSize = 12.sp
    )

    androidx.compose.material3.TextButton(
        onClick = { showCellFallback = !showCellFallback }
    ) {
        Text(
            if (showCellFallback) "Hide alternative square selector"
            else "Trouble tapping a square? Use row / column buttons",
            color = OverlayGold, fontSize = 12.sp
        )
    }
    if (showCellFallback) {
        Text(
            "These are GRID COORDINATES, not picture-tile numbers.",
            color = OverlayParchment, fontSize = 11.sp
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0..4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (c in 0..4) {
                        val position = r * 5 + c
                        androidx.compose.material3.OutlinedButton(
                            onClick = { selectSquare(position) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                if (selectedPosition == position) 3.dp else 1.dp,
                                OverlayGold
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 0.dp, vertical = 0.dp
                            )
                        ) {
                            Text(
                                "${r + 1},${c + 1}", fontSize = 12.sp,
                                color = OverlayParchment, textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    Text(
        "STEP 2 — SELECT THE MATCHING PICTURE PIECE",
        color = OverlayGold, fontSize = 13.sp, fontWeight = FontWeight.Bold
    )
    Text(
        "Tap where the selected square's picture belongs in the COMPLETED image below. The 5×5 reference picture is tappable. For a blank square, tap SET EMPTY.",
        color = OverlayParchment, fontSize = 12.sp
    )
    SelectablePuzzlePicture(
        bitmap = reference,
        selectedPosition = -1,
        enabled = selectedPosition in 0..24,
        onPress = onChooseTile
    )
    androidx.compose.material3.Button(
        onClick = { onChooseTile(24) },
        enabled = selectedPosition in 0..24,
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = OverlayGold, contentColor = Color.Black
        )
    ) {
        Text("SET SELECTED SQUARE TO EMPTY", fontWeight = FontWeight.Bold)
    }
    Text(
        "The bottom-right square in the solved reference is the empty destination. The app keeps one copy of every picture piece; corrections swap identities and do not change the real screenshot.",
        color = OverlayParchment, fontSize = 11.sp
    )
}

/**
 * A single reliable touch surface. Previous layered Row/Column cell overlays
 * used fillMaxSize with weights and could miss or misalign tap hit targets.
 * This uses the actual image bounds to hit-test all 25 cells consistently.
 */
@Composable
private fun SelectablePuzzlePicture(
    bitmap: Bitmap,
    selectedPosition: Int,
    enabled: Boolean = true,
    onPress: (Int) -> Unit
) {
    val touchModifier = if (enabled) {
        Modifier.pointerInput(onPress) {
            androidx.compose.foundation.gestures.detectTapGestures { offset ->
                if (size.width > 0 && size.height > 0) {
                    val col = (offset.x / size.width * 5f).toInt().coerceIn(0, 4)
                    val row = (offset.y / size.height * 5f).toInt().coerceIn(0, 4)
                    onPress(row * 5 + col)
                }
            }
        }
    } else Modifier

    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .border(2.dp, OverlayGold)
            .then(touchModifier)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "5 by 5 puzzle artwork; tap a square to choose its picture",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = size.width / 5f
            val cellH = size.height / 5f
            val thin = 1.dp.toPx()
            for (i in 1..4) {
                drawLine(
                    OverlayParchment.copy(alpha = 0.65f),
                    start = androidx.compose.ui.geometry.Offset(i * cellW, 0f),
                    end = androidx.compose.ui.geometry.Offset(i * cellW, size.height),
                    strokeWidth = thin
                )
                drawLine(
                    OverlayParchment.copy(alpha = 0.65f),
                    start = androidx.compose.ui.geometry.Offset(0f, i * cellH),
                    end = androidx.compose.ui.geometry.Offset(size.width, i * cellH),
                    strokeWidth = thin
                )
            }
            if (selectedPosition in 0..24) {
                val x = selectedPosition % 5 * cellW
                val y = selectedPosition / 5 * cellH
                drawRect(
                    color = OverlayGold.copy(alpha = 0.20f),
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(cellW, cellH)
                )
                drawRect(
                    color = OverlayGold,
                    topLeft = androidx.compose.ui.geometry.Offset(x + 2f, y + 2f),
                    size = androidx.compose.ui.geometry.Size(cellW - 4f, cellH - 4f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )
            }
        }
    }
}

/** Tap any of the actual 5x5 screenshot cells to correct a mistaken gap. */
@Composable
fun EmptySpacePicker(screenshot: Bitmap, onSelect: (Int) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OverlayDark),
        border = BorderStroke(2.dp, OverlayGold)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp)
        ) {
            Image(
                bitmap = screenshot.asImageBitmap(),
                contentDescription = "Tap the actual empty square in the original puzzle screenshot",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0 until 5) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (col in 0 until 5) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .border(1.dp, OverlayGold.copy(alpha = 0.50f))
                                    .clickable { onSelect(row * 5 + col) }
                            )
                        }
                    }
                }
            }
        }
    }
}

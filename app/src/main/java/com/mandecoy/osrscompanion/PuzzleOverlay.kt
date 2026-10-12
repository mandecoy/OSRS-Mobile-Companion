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
 * Manual corrections NEVER depend on tapping the (possibly mismatched) solved
 * artwork. All 25 positions including the empty square are selectable.
 *
 * One option: tap two positions of the ACTUAL screenshot to swap the mistaken
 * labels. Another: tap a screenshot position then choose its SOLVED position
 * from explicit 1..24 / EMPTY buttons. Both operations preserve the permutation.
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
    Text(
        "FIX TILES: Tap a tile on the screenshot, then tap ANOTHER screenshot tile to swap their labels. Every tile, including EMPTY, can be selected.",
        color = OverlayParchment,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
    )
    LabeledPuzzleCells(
        bitmap = screenshot,
        labels = board,
        selectedPosition = selectedPosition,
        onPress = { position ->
            if (selectedPosition == -1 || selectedPosition == position) {
                onSelectPosition(if (selectedPosition == position) -1 else position)
            } else {
                onSwapPosition(position)
            }
        }
    )
    Text(
        if (selectedPosition in 0..24)
            "Selected: row ${selectedPosition / 5 + 1}, column ${selectedPosition % 5 + 1} (recognized as ${if (board[selectedPosition] == 24) "EMPTY" else "tile " + (board[selectedPosition] + 1)}). Tap a second screenshot square to swap, OR choose its correct solved-position number below."
        else
            "No tile selected. Tap any of the 25 squares above to begin.",
        color = OverlayGold, fontSize = 13.sp
    )
    Text(
        "SOLVED REFERENCE (for comparison; the image itself is not a button):",
        color = OverlayParchment,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    )
    Image(
        bitmap = reference.asImageBitmap(),
        contentDescription = "Complete puzzle artwork, reference for assigning tile numbers",
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .border(2.dp, OverlayGold)
    )
    Text(
        "ASSIGN SELECTED TILE TO SOLVED POSITION: Choose 1–24, or EMPTY, using these buttons. Choosing a number swaps its assignment with the tile currently assigned that number.",
        color = OverlayParchment,
        fontSize = 12.sp
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (row in 0..4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (col in 0..4) {
                    val target = row * 5 + col
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onChooseTile(target) },
                        enabled = selectedPosition in 0..24,
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 0.dp, vertical = 0.dp
                        ),
                        border = BorderStroke(
                            if (selectedPosition in 0..24 && board[selectedPosition] == target)
                                3.dp else 1.dp,
                            OverlayGold
                        )
                    ) {
                        Text(
                            if (target == 24) "E" else (target + 1).toString(),
                            fontSize = 14.sp,
                            color = OverlayParchment,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
    Text(
        "E = EMPTY. All 25 assignments are available. Tap screenshot squares or the numbered buttons; you can make as many corrections as needed before recalculating.",
        color = OverlayParchment,
        fontSize = 11.sp
    )
}

@Composable
private fun LabeledPuzzleCells(
    bitmap: Bitmap,
    labels: IntArray,
    selectedPosition: Int,
    onPress: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        .border(2.dp, OverlayGold)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Actual OSRS screenshot; tap any tile to select and swap its recognized number",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0 until 5) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (c in 0 until 5) {
                        val pos = r * 5 + c
                        val tile = labels[pos]
                        Box(
                            modifier = Modifier.weight(1f).fillMaxSize()
                                .border(
                                    if (selectedPosition == pos) 3.dp else 0.5.dp,
                                    if (selectedPosition == pos) OverlayGold
                                    else Color.White.copy(alpha = 0.45f)
                                )
                                .clickable { onPress(pos) },
                            contentAlignment = Alignment.TopStart
                        ) {
                            Text(
                                if (tile == 24) "E" else (tile + 1).toString(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .background(
                                        if (selectedPosition == pos)
                                            Color(0xff855400)
                                        else Color.Black.copy(alpha = 0.82f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
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

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


private fun renderPuzzleAtMove(solved: PuzzleAnalysis, moveCount: Int): Bitmap {
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

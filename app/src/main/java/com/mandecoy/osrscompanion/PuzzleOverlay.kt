package com.mandecoy.osrscompanion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
    val hints = PuzzleEngine.moveHints(solved.board, solved.moves, start, 5)
    val gap = PuzzleEngine.gapPositionAt(solved.board, solved.moves, start)

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
                bitmap = solved.preview.asImageBitmap(),
                contentDescription = "Uploaded Puzzle Box with move overlays",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0 until 5) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (col in 0 until 5) {
                            val position = row * 5 + col
                            val cellHints = hints.filter { it.position == position }

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
                                    Column(
                                        modifier = Modifier
                                            .background(
                                                OverlayGold.copy(alpha = 0.94f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        cellHints.forEach { hint ->
                                            Text(
                                                hint.moveNumber.toString() + hint.arrow,
                                                color = Color.Black,
                                                fontSize = if (cellHints.size > 1) 10.sp else 13.sp,
                                                lineHeight = 13.sp,
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

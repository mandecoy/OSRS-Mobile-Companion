package com.mandecoy.osrscompanion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

private val LbDark = Color(0xff201c18)
private val LbCard = Color(0xff2e2924)
private val LbOff = Color(0xff4a433b)
private val LbGold = Color(0xffd7a93f)
private val LbGoldDark = Color(0xff8b6722)
private val LbText = Color(0xfff4ead4)
private val LbParchment = Color(0xffd7c7a4)
private val LbSolved = Color(0xff6f8b46)

@Composable
fun NativeLightBoxScreen(nav: NavHostController) {
    var lights by remember { mutableStateOf(BooleanArray(25)) }
    var stage by remember { mutableStateOf("initial") }
    var currentSwitch by remember { mutableIntStateOf(0) }
    var previousState by remember { mutableStateOf(BooleanArray(25)) }
    val changes = remember { arrayOfNulls<BooleanArray>(8) }
    var solution by remember { mutableStateOf<List<Int>>(emptyList()) }
    var solutionStep by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    fun resetAll() {
        lights = BooleanArray(25)
        stage = "initial"
        currentSwitch = 0
        previousState = BooleanArray(25)
        for (i in changes.indices) changes[i] = null
        solution = emptyList()
        solutionStep = 0
        error = null
    }

    fun toggleLight(index: Int) {
        if (stage == "solution") return
        val copy = lights.copyOf()
        copy[index] = !copy[index]
        lights = copy
    }

    fun beginCalibration() {
        previousState = lights.copyOf()
        currentSwitch = 0
        stage = "calibrate"
        error = null
    }

    fun recordCurrentSwitch() {
        changes[currentSwitch] = xorLights(previousState, lights)
        previousState = lights.copyOf()

        if (currentSwitch < 7) {
            currentSwitch++
        } else {
            val solved = solveLightBox(lights, changes)
            if (solved == null) {
                error = "No solution found. Reset and repeat the A–H calibration carefully."
                stage = "initial"
            } else {
                solution = solved
                solutionStep = 0
                stage = "solution"
                error = null
            }
        }
    }

    Screen(nav, "Light Box Solver") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(LbDark),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    when (stage) {
                        "initial" -> "First, copy the lights currently ON in your OSRS Light Box."
                        "calibrate" -> {
                            val letter = ('A'.code + currentSwitch).toChar()
                            val position = if (currentSwitch == 0) "left-most" else if (currentSwitch == 7) "right-most" else "switch " + letter
                            "Press $letter in OSRS once ($position). Then update the 5×5 grid below so it matches your game."
                        }
                        else -> {
                            if (solution.isEmpty()) "The Light Box should already be solved."
                            else {
                                val letter = ('A'.code + solution[solutionStep]).toChar()
                                "Press the highlighted $letter switch in OSRS."
                            }
                        }
                    },
                    color = LbParchment,
                    fontSize = 16.sp
                )
            }

            item {
                SwitchGuide(
                    stage = stage,
                    currentSwitch = currentSwitch,
                    solution = solution,
                    solutionStep = solutionStep
                )
            }

            item {
                Text(
                    "A is the left-most switch and H is the right-most switch.",
                    color = LbParchment,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                LightGrid(
                    lights = lights,
                    enabled = stage != "solution",
                    onToggle = { toggleLight(it) }
                )
            }

            error?.let { message ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xff4a302a)),
                        border = BorderStroke(1.dp, LbGoldDark)
                    ) {
                        Text(message, color = LbText, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            item {
                when (stage) {
                    "initial" -> {
                        Button(
                            onClick = { beginCalibration() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = LbGold, contentColor = Color.Black)
                        ) {
                            Text("Start A–H Calibration")
                        }
                    }
                    "calibrate" -> {
                        val letter = ('A'.code + currentSwitch).toChar()
                        Button(
                            onClick = { recordCurrentSwitch() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = LbGold, contentColor = Color.Black)
                        ) {
                            Text(if (currentSwitch < 7) "I pressed $letter — Next Switch" else "I pressed H — Find Solution")
                        }
                    }
                    "solution" -> {
                        if (solution.isEmpty()) {
                            Button(
                                onClick = { resetAll() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = LbGold, contentColor = Color.Black)
                            ) {
                                Text("Start Over")
                            }
                        } else {
                            val isLast = solutionStep == solution.lastIndex
                            Button(
                                onClick = {
                                    if (isLast) {
                                        resetAll()
                                    } else {
                                        solutionStep++
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = LbGold, contentColor = Color.Black)
                            ) {
                                Text(if (isLast) "Done — Start Over" else "Next Switch")
                            }
                        }
                    }
                }
            }

            if (stage != "initial") {
                item {
                    OutlinedButton(
                        onClick = { resetAll() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Solver", color = LbText)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchGuide(
    stage: String,
    currentSwitch: Int,
    solution: List<Int>,
    solutionStep: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            for (i in 0..7) {
                val isCalibrationTarget = stage == "calibrate" && i == currentSwitch
                val isSolutionMember = stage == "solution" && i in solution
                val isSolutionTarget = stage == "solution" && solution.isNotEmpty() && solution[solutionStep] == i
                val background = when {
                    isSolutionTarget -> LbGold
                    isCalibrationTarget -> LbGold
                    isSolutionMember -> LbSolved
                    else -> LbCard
                }
                val foreground = if (isSolutionTarget || isCalibrationTarget) Color.Black else LbText
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = background),
                    border = BorderStroke(1.dp, LbGoldDark)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            ('A'.code + i).toChar().toString(),
                            color = foreground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                }
            }
        }

        if (stage == "solution" && solution.isNotEmpty()) {
            Text(
                "Solution: " + solution.joinToString(" → ") { (('A'.code + it).toChar()).toString() },
                color = LbGold,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LightGrid(
    lights: BooleanArray,
    enabled: Boolean,
    onToggle: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LbCard, RoundedCornerShape(10.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        for (row in 0 until 5) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (col in 0 until 5) {
                    val index = row * 5 + col
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(
                                if (lights[index]) LbGold else LbOff,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable(enabled = enabled) { onToggle(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (lights[index]) "ON" else "",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun xorLights(a: BooleanArray, b: BooleanArray): BooleanArray =
    BooleanArray(25) { index -> a[index] xor b[index] }

private fun solveLightBox(
    current: BooleanArray,
    changes: Array<BooleanArray?>
): List<Int>? {
    if (changes.any { it == null }) return null

    for (mask in 0 until 256) {
        val state = current.copyOf()
        val pressed = ArrayList<Int>()

        for (switch in 0 until 8) {
            if ((mask and (1 shl switch)) != 0) {
                pressed.add(switch)
                val diff = changes[switch] ?: return null
                for (i in 0 until 25) state[i] = state[i] xor diff[i]
            }
        }

        if (state.all { it }) return pressed
    }
    return null
}

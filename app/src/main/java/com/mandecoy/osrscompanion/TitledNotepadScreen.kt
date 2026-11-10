package com.mandecoy.osrscompanion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import java.util.UUID

private val NoteGold = Color(0xffd7a93f)
private val NoteParchment = Color(0xffd7c7a4)
private val NoteWhite = Color(0xfff4ead4)
private val NoteStone = Color(0xff2e2924)

/**
 * Stable tabs above a flexible editor. IME padding plus Android adjustResize
 * shrink the editor to stay above the Samsung keyboard. The multiline field
 * scrolls internally to follow the caret when editing a long note.
 */
@Composable
fun TitledNotepadScreen(nav: NavHostController) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var notes by remember { mutableStateOf(TitledNotepadStore.load(context)) }
    var selectedId by remember { mutableStateOf(TitledNotepadStore.selected(context, notes)) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun persist(next: List<NotepadSection>, select: String? = selectedId) {
        val target = NotepadDocument.selected(next, select)
        notes = next
        selectedId = target
        TitledNotepadStore.save(context, next, target)
    }

    val current = notes.firstOrNull { it.id == selectedId }
    Screen(nav, "Notepad") { inset ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inset).imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("MY NOTES", color = NoteGold, fontWeight = FontWeight.Bold)
                    Text("${notes.size} saved • automatically saved on this device",
                        color = NoteParchment, fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = {
                        keyboard?.hide()
                        val newId = UUID.randomUUID().toString()
                        persist(NotepadDocument.newSection(notes, newId), newId)
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text("+ Add note", color = NoteGold, fontSize = 12.sp)
                }
            }

            // Scroll sideways through titled notes; each has its own content.
            if (notes.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(notes, key = { it.id }) { section ->
                        val active = section.id == selectedId
                        Card(
                            modifier = Modifier.clickable {
                                if (selectedId != section.id) {
                                    keyboard?.hide()
                                    selectedId = section.id
                                    TitledNotepadStore.save(context, notes, section.id)
                                }
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (active) Color(0xff544227) else NoteStone
                            ),
                            border = BorderStroke(
                                if (active) 2.dp else 1.dp,
                                if (active) NoteGold else Color(0xff8b6722)
                            ),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text(
                                NotepadDocument.visibleTitle(section),
                                color = if (active) NoteWhite else NoteParchment,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (current == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NoteStone),
                    border = BorderStroke(1.dp, NoteGold)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("No notes yet", color = NoteWhite, fontWeight = FontWeight.Bold)
                        Text("Tap + Add note to create a titled note.",
                            color = NoteParchment, fontSize = 12.sp)
                    }
                }
            } else {
                androidx.compose.runtime.key(current.id) {
                    OutlinedTextField(
                        value = current.title,
                        onValueChange = { title ->
                            persist(NotepadDocument.title(notes, current.id, title))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Note title") },
                        placeholder = { Text("Give this note a title") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() })
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("CONTENT", color = NoteGold, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                        TextButton(onClick = {
                            keyboard?.hide()
                            confirmDelete = true
                        }) {
                            Text("Delete note", color = Color(0xffffb39c), fontSize = 12.sp)
                        }
                    }
                    // Keyboard shrinking leaves only the visible height for
                    // the text field. It scrolls long text to the cursor.
                    OutlinedTextField(
                        value = current.body,
                        onValueChange = { text ->
                            persist(NotepadDocument.body(notes, current.id, text))
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = { Text("Write your note") },
                        placeholder = { Text("Type here…") },
                        singleLine = false
                    )
                }
            }
        }
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = {
                Text("\"${NotepadDocument.visibleTitle(current)}\" will be deleted. " +
                    "Your other notes will remain saved.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val next = NotepadDocument.remove(notes, current.id)
                    persist(next, next.firstOrNull()?.id)
                    confirmDelete = false
                }) {
                    Text("Delete", color = Color(0xffb84236))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = NoteGold)
                }
            }
        )
    }
}

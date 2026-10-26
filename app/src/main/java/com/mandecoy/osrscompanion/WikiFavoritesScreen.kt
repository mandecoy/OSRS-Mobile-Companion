package com.mandecoy.osrscompanion

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController

private val FavorGold = Color(0xffd7a93f)
private val FavorText = Color(0xfff4ead4)
private val FavorStone = Color(0xff2e2924)

@Composable
fun WikiFavoritesScreen(nav: NavHostController) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val favorites = remember(revision) { WikiFavoriteStore.all(context) }
    Screen(nav, "Wiki Favorites") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Save a Wiki URL as its own homepage widget. Open the original Wiki page in the app or in a floating window while playing.",
                    color = FavorText
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = FavorStone),
                    border = BorderStroke(1.dp, FavorGold)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = url, onValueChange = { url = it.take(600) },
                            label = { Text("Paste OSRS Wiki URL") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = title, onValueChange = { title = it.take(42) },
                            label = { Text("Widget name (optional)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val added = WikiFavoriteStore.add(context, title, url)
                                if (added == null) {
                                    message = "Please enter an https://oldschool.runescape.wiki/ link."
                                } else {
                                    message = "Created widget: " + added.title
                                    title = ""
                                    url = ""
                                    revision++
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Create Wiki widget") }
                        if (message.isNotBlank()) Text(message, color = FavorGold)
                    }
                }
            }
            item {
                Text("MY INDIVIDUAL WIKI WIDGETS", color = FavorGold, fontWeight = FontWeight.Bold)
                if (favorites.isEmpty()) Text("No favorites saved yet.", color = FavorText)
            }
            items(favorites, key = { it.id }) { favorite ->
                Card(colors = CardDefaults.cardColors(containerColor = FavorStone)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(favorite.title, color = FavorText, fontWeight = FontWeight.Bold)
                        Text(favorite.url, color = FavorGold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { nav.navigate("favorite/" + favorite.id) }
                            ) { Text("In App") }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (!Settings.canDrawOverlays(context)) {
                                        context.startActivity(
                                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:" + context.packageName))
                                        )
                                    } else {
                                        ContextCompat.startForegroundService(
                                            context,
                                            Intent(context, FloatingOverlayService::class.java)
                                                .setAction(FloatingOverlayService.ACTION_OPEN_FAVORITE)
                                                .putExtra("favorite_id", favorite.id)
                                        )
                                    }
                                }
                            ) { Text("Float") }
                            OutlinedButton(onClick = {
                                WikiFavoriteStore.remove(context, favorite.id)
                                revision++
                            }) { Text("×") }
                        }
                    }
                }
            }
        }
    }
}

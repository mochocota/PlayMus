package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.MusicRepository
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.components.MiniPlayer
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary
import java.text.Normalizer

private fun norm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().trim()

@Composable
fun SearchScreen(nav: NavHostController, vm: PlayerViewModel) {
    val songs by MusicRepository.songs.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize().background(NavyBg)) {
        Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                    modifier = Modifier.clickable { nav.popBackStack() })
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    singleLine = true,
                    placeholder = { Text("Canciones, artistas, géneros…", color = TextSecondary, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, null, tint = TextSecondary)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        cursorColor = Color.White, focusedBorderColor = Purple,
                        unfocusedBorderColor = TextSecondary
                    )
                )
            }
            Spacer(Modifier.height(14.dp))

            if (songs.isEmpty()) {
                Text("Tu biblioteca está vacía.\nSincroniza tu canal de Telegram primero.",
                    color = TextSecondary, fontSize = 14.sp)
            } else if (query.isBlank()) {
                Text("Busca por título, artista, álbum o género.",
                    color = TextSecondary, fontSize = 14.sp)
            } else {
                val nq = norm(query)

                // Hasta 3 sugerencias que se acercan a lo escrito
                val suggestions = buildList {
                    songs.forEach { if (norm(it.title).contains(nq)) add(it.title) }
                    songs.forEach { if (norm(it.artist).contains(nq)) add(it.artist) }
                    songs.forEach { if (it.album.isNotBlank() && norm(it.album).contains(nq)) add(it.album) }
                    songs.forEach { if (norm(it.genre).contains(nq)) add(it.genre) }
                }.distinctBy { norm(it) }.take(3)

                Text("Sugerencias", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    suggestions.forEach { s ->
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(CardLighter)
                                .clickable { query = s; keyboard?.hide() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("🔎 $s", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Resultados en vivo (cuadrícula de 3 columnas)
                val results = songs.filter {
                    norm(it.title).contains(nq) || norm(it.artist).contains(nq) ||
                            norm(it.album).contains(nq) || norm(it.genre).contains(nq)
                }
                Text("Resultados (${results.size})",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                if (results.isEmpty()) {
                    Text("Sin resultados para «$query»", color = TextSecondary, fontSize = 13.sp)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(results.size) { i ->
                            val s = results[i]
                            Column(Modifier.clickable { vm.play(s, results) }) {
                                CoverImage(s, Modifier.fillMaxWidth().aspectRatio(1f), corner = 12.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(s.title, color = Color.White, fontSize = 12.sp,
                                    maxLines = 1, lineHeight = 14.sp)
                                Text(s.artist, color = TextSecondary, fontSize = 10.sp,
                                    maxLines = 1, lineHeight = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        if (vm.current != null) MiniPlayer(vm) { nav.navigate("player") }
    }
}

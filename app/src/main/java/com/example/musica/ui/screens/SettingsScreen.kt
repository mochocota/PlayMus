package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.DownloadRepository
import com.example.musica.data.MusicRepository
import com.example.musica.telegram.TelegramClient
import com.example.musica.telegram.TelegramConfig
import com.example.musica.ui.theme.CardDark
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.Cyan
import com.example.musica.ui.theme.GradientBrush
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Pink
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun SettingRow(icon: ImageVector,
                       label: String, value: String? = null,
                       onClick: (() -> Unit)? = null,
                       trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth()
        .clickable(enabled = onClick != null) { onClick?.invoke() }
        .clip(RoundedCornerShape(12.dp))
        .background(if (onClick != null) CardLighter else CardDark)
        .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (onClick != null) Color.White else TextSecondary,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label,
            color = if (onClick != null) Color.White else Color.White.copy(alpha = 0.8f),
            fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = TextSecondary, fontSize = 13.sp)
        trailing()
    }
}

@Composable
fun SettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tgConnected = TelegramConfig.token(ctx).isNotBlank()
    var sharing by remember { mutableStateOf(false) }
    var shareMsg by remember { mutableStateOf("") }

    // Modo offline real (persistente)
    var offline by remember { mutableStateOf(DownloadRepository.isOffline()) }
    val dlCount = DownloadRepository.downloadedCount()

    Column(Modifier.fillMaxSize().background(NavyBg)
        .verticalScroll(rememberScrollState()).padding(16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                modifier = Modifier.clickable { nav.popBackStack() })
            Text("  Configuración", color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))

        Text("Biblioteca", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))

        SettingRow(
            Icons.Filled.Cloud, "Canal de Telegram",
            if (tgConnected) "Conectado" else "Sin configurar"
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = Cyan)
        }
        Spacer(Modifier.height(8.dp))

        SettingRow(
            Icons.Filled.VpnKey, "Nuevo token", null,
            onClick = { nav.navigate("telegram") }
        ) {
            Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary)
        }
        Spacer(Modifier.height(8.dp))

        // ---------- MODO OFFLINE (real) ----------
        SettingRow(
            Icons.Filled.CloudOff, "Modo offline",
            if (offline) "ON · $dlCount descargas" else "OFF"
        ) {
            Switch(
                checked = offline,
                onCheckedChange = {
                    DownloadRepository.setOfflineMode(it)
                    offline = it
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Purple)
            )
        }
        if (offline) {
            Text(
                "Mostrando solo tus $dlCount canciones descargadas. Funcionan sin internet.",
                color = Cyan, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        // ---------- Compartir biblioteca ----------
        SettingRow(
            Icons.Filled.IosShare, "Compartir biblioteca", null,
            onClick = {
                if (sharing) return@SettingRow
                scope.launch(Dispatchers.IO) {
                    val chatId = TelegramConfig.importChatId(ctx)
                    if (chatId == 0L) {
                        withContext(Dispatchers.Main) {
                            shareMsg = "Primero envía melodix_import.json al bot (así sabe a dónde mandarte la biblioteca)"
                        }
                        return@launch
                    }
                    val f = MusicRepository.libraryFile()
                    if (!f.exists()) {
                        withContext(Dispatchers.Main) {
                            shareMsg = "La biblioteca está vacía."
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) { sharing = true; shareMsg = "Enviando biblioteca…" }
                    try {
                        TelegramClient(TelegramConfig.token(ctx))
                            .sendDocument(chatId, f, "melodix_import.json")
                        withContext(Dispatchers.Main) {
                            shareMsg = "✓ Enviada al chat del bot. Descárgala, renómbrala a seed_catalog.json y reemplázala en res/raw de GitHub."
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            shareMsg = "Error: ${e.message}"
                        }
                    } finally {
                        withContext(Dispatchers.Main) { sharing = false }
                    }
                }
            }
        ) {
            Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary)
        }
        if (shareMsg.isNotBlank()) {
            Text(shareMsg, color = if (shareMsg.startsWith("Error")) Pink else Cyan,
                fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }

        // ---------- Firma del diseñador ----------
        Spacer(Modifier.height(48.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.clip(RoundedCornerShape(22.dp))
                    .background(CardDark)
                    .padding(horizontal = 34.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier
                    .width(44.dp).height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GradientBrush))
                Spacer(Modifier.height(16.dp))
                Text("DISEÑO DE LA APLICACIÓN",
                    color = TextSecondary, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "WGM",
                    style = TextStyle(
                        brush = GradientBrush,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 6.sp
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text("Melodix", color = TextSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            }
        }
    }
}

package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.telegram.TelegramConfig
import com.example.musica.ui.theme.Cyan
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary

@Composable
fun SettingRow(icon: ImageVector, label: String, value: String? = null,
               onClick: (() -> Unit)? = null,
               trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth()
        .clickable(enabled = onClick != null) { onClick?.invoke() }
        .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (onClick != null) TextSecondary else TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (onClick != null) Color.White else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = TextSecondary, fontSize = 13.sp)
        trailing()
    }
}

@Composable
fun SettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val tgConnected = TelegramConfig.token(ctx).isNotBlank()
    var sync by remember { mutableStateOf(true) }
    var offline by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(NavyBg)
        .verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                modifier = Modifier.clickable { nav.popBackStack() })
            Text("  Configuración", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))

        // ---------- Biblioteca ----------
        Text("Biblioteca", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))

        // Canal conectado: DESHABILITADO (solo informativo)
        SettingRow(
            Icons.Filled.Cloud, "Canal de Telegram",
            if (tgConnected) "Conectado" else "Sin configurar"
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = Cyan)
        }

        // Nuevo token: para guardar otro bot (con prueba de conexión)
        SettingRow(
            Icons.Filled.VpnKey, "Nuevo token", null,
            onClick = { nav.navigate("telegram") }
        ) {
            Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary)
        }
        Spacer(Modifier.height(14.dp))

        Text("Cuenta", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        SettingRow(Icons.Filled.Person, "Tu perfil") {
            Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary)
        }
        SettingRow(Icons.Filled.Sync, "Sincronización") {
            Switch(checked = sync, onCheckedChange = { sync = it },
                colors = SwitchDefaults.colors(checkedTrackColor = Purple))
        }
        Spacer(Modifier.height(14.dp))

        Text("Reproducción", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        SettingRow(Icons.Filled.Tune, "Calidad de audio", "Alta") {
            Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary)
        }
        SettingRow(Icons.Filled.Equalizer, "Ecualizador") {
            Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary)
        }
        SettingRow(Icons.Filled.CloudOff, "Modo offline") {
            Switch(checked = offline, onCheckedChange = { offline = it },
                colors = SwitchDefaults.colors(checkedTrackColor = Purple))
        }
        Spacer(Modifier.height(14.dp))

        Text("Notificaciones", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        SettingRow(Icons.Filled.Notifications, "Nuevos lanzamientos") {
            Switch(checked = true, onCheckedChange = {},
                colors = SwitchDefaults.colors(checkedTrackColor = Purple))
        }
        Spacer(Modifier.height(14.dp))

        Text("Otros", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        SettingRow(Icons.AutoMirrored.Filled.HelpOutline, "Ayuda") {
            Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary)
        }
        SettingRow(Icons.Filled.Info, "Acerca de") {
            Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary)
        }
    }
}

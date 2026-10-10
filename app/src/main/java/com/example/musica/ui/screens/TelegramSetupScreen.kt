package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
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

@Composable
fun TelegramSetupScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(TelegramConfig.token(ctx)) }
    var canal by remember { mutableStateOf(TelegramConfig.channel(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(NavyBg)
        .verticalScroll(rememberScrollState()).padding(16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                modifier = Modifier.clickable { nav.popBackStack() })
            Text("  Canal de Telegram", color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(CardDark).padding(14.dp)) {
            Text(
                "Cómo conectar tu canal 📻\n\n" +
                "1. Habla con @BotFather en Telegram → envía /newbot → dale un nombre → copia el TOKEN que te entrega.\n\n" +
                "2. Crea un canal de música y añade a tu bot como Administrador.\n\n" +
                "3. Sube canciones como archivo de audio al canal. En el texto del mensaje escribe el género con # :  #rock #pop #salsa #balada #grupera…\n\n" +
                "4. Guarda el token aquí y pulsa «Sincronizar» en Biblioteca.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp
            )
        }
        Spacer(Modifier.height(18.dp))

        Text("Token del bot", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Ej: 7123456789:AAH…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                cursorColor = Color.White, focusedBorderColor = Purple,
                unfocusedBorderColor = TextSecondary, focusedLabelColor = Purple,
                unfocusedLabelColor = TextSecondary
            )
        )
        Spacer(Modifier.height(12.dp))
        Text("Nombre del canal (opcional)", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = canal, onValueChange = { canal = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Ej: @mi_canal_de_musica") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                cursorColor = Color.White, focusedBorderColor = Purple,
                unfocusedBorderColor = TextSecondary, focusedLabelColor = Purple,
                unfocusedLabelColor = TextSecondary
            )
        )
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                .background(CardLighter).clickable(enabled = !busy) {
                    busy = true; result = "Probando…"
                    scope.launch(Dispatchers.IO) {
                        result = try {
                            "@${TelegramClient(token.trim()).getMe()} conectado ✓"
                        } catch (e: Exception) { "Error: ${e.message}" }
                        busy = false
                    }
                }, contentAlignment = Alignment.Center) {
                Text(if (busy) "Probando…" else "Probar conexión",
                    color = Color.White, fontSize = 14.sp)
            }
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                .background(GradientBrush).clickable {
                    TelegramConfig.save(ctx, token, canal)
                    result = "Guardado ✓"
                }, contentAlignment = Alignment.Center) {
                Text("Guardar", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (result.isNotBlank()) {
            Text(result, color = if (result.startsWith("Error")) Pink else Cyan, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text("Nota: los bots de Telegram solo descargan archivos de hasta 20 MB.",
            color = TextSecondary, fontSize = 12.sp)
    }
}

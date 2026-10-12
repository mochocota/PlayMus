package com.example.musica.telegram

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// ---------- Configuración guardada en el teléfono ----------
object TelegramConfig {
    private const val PREFS = "telegram_prefs"

    // ============================================================
    // 👇👇 TU TOKEN DEL BOT (ya integrado en la app) 👇👇
    // ============================================================
    const val DEFAULT_BOT_TOKEN = "8990152902:AAG70gBYSAEnXUtbvdY_n5wBcJhFa6UkO5A"

    // ============================================================
    // 👇👇 IMPORTACIÓN AUTOMÁTICA ENTRE DISPOSITIVOS 👇👇
    //
    // Pega aquí el file_id de tu melodix_import.json y CUALQUIER
    // dispositivo nuevo importará toda la biblioteca en su
    // primera sincronización, sin volver a enviar nada.
    //
    // ¿De dónde saco el file_id? Cuando la app procesa el archivo
    // de importación, el mensaje de estado de Biblioteca muestra:
    //   "file_id: AQADBAAT..."
    // Copia esa cadena completa y pégala aquí entre las comillas.
    // ============================================================
    const val DEFAULT_IMPORT_FILE_ID = "CQACAgEAAyEGAAMBAozBXgACHdNqy0eiEK_CV1hrXMjCeQgXve8HmAAC3wkAAtTZKUbW7WojDK2hfh4E"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Devuelve el token guardado en el teléfono; si no hay,
    // usa el integrado en el código (DEFAULT_BOT_TOKEN).
    fun token(ctx: Context): String {
        val saved = prefs(ctx).getString("token", "") ?: ""
        return saved.ifBlank { DEFAULT_BOT_TOKEN.trim() }
    }

    // file_id del archivo de importación: primero se busca el
    // guardado en este teléfono; si no hay, el integrado en el código
    fun autoImportFileId(ctx: Context): String {
        val saved = prefs(ctx).getString("auto_import_file_id", "") ?: ""
        return saved.ifBlank { DEFAULT_IMPORT_FILE_ID.trim() }
    }

    fun saveAutoImportFileId(ctx: Context, fid: String) {
        prefs(ctx).edit().putString("auto_import_file_id", fid).apply()
    }

    fun channel(ctx: Context): String = prefs(ctx).getString("channel", "") ?: ""
    fun offset(ctx: Context): Long = prefs(ctx).getLong("offset", 0L)

    fun save(ctx: Context, token: String, channel: String) {
        prefs(ctx).edit().putString("token", token.trim()).putString("channel", channel.trim()).apply()
    }

    fun saveOffset(ctx: Context, offset: Long) {
        prefs(ctx).edit().putLong("offset", offset).apply()
    }
}

// ---------- Canción que llega desde Telegram ----------
data class TgAudio(
    val fileId: String,
    val title: String,
    val artist: String,
    val duration: Int,
    val fileSize: Long,
    val caption: String,
    val fileName: String
)

// ---------- Cliente de la API de Bot de Telegram ----------
class TelegramClient(private val token: String) {

    private fun api(method: String, params: String, timeoutSec: Int = 15): JSONObject {
        val conn = URL("https://api.telegram.org/bot$token/$method")
            .openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = (timeoutSec + 10) * 1000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(params.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) throw IOException("HTTP $code: $body")
            val json = JSONObject(body)
            if (!json.optBoolean("ok")) throw IOException(json.optString("description", "Error de Telegram"))
            json
        } finally {
            conn.disconnect()
        }
    }

    fun getMe(): String = api("getMe", "{}").getJSONObject("result").optString("username")

    fun deleteWebhook() {
        try { api("deleteWebhook", "{}") } catch (_: Exception) {}
    }

    fun getUpdates(offset: Long, timeoutSec: Int = 10): List<JSONObject> {
        val res = api(
            "getUpdates",
            """{"offset":$offset,"timeout":$timeoutSec,"allowed_updates":["message","channel_post"]}""",
            timeoutSec
        )
        val arr = res.getJSONArray("result")
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    fun getFilePath(fileId: String): String {
        val res = api("getFile", """{"file_id":"$fileId"}""")
        return res.getJSONObject("result").optString("file_path", "")
    }

    fun fileUrl(filePath: String): String = "https://api.telegram.org/file/bot$token/$filePath"

    companion object {
        // Extrae el audio de un "update" (publicación de canal o mensaje)
        fun extractAudio(update: JSONObject): TgAudio? {
            val msg = update.optJSONObject("channel_post")
                ?: update.optJSONObject("message")
                ?: return null
            val audio = msg.optJSONObject("audio")
                ?: msg.optJSONObject("document")
                    ?.takeIf { it.optString("mime_type", "").startsWith("audio") }
                ?: return null
            val fileId = audio.optString("file_id", "")
            if (fileId.isEmpty()) return null

            val fileName = audio.optString("file_name", "")
            val baseName = fileName.replace(
                Regex("\\.(mp3|m4a|ogg|wav|flac|opus)$", RegexOption.IGNORE_CASE), ""
            )
            var title = audio.optString("title", "")
            var artist = audio.optString("performer", "")
            if (title.isEmpty()) {
                val parts = baseName.split(" - ", limit = 2)
                if (parts.size == 2) { artist = parts[0].trim(); title = parts[1].trim() }
                else title = baseName
            }
            if (artist.isEmpty()) artist = "Desconocido"
            if (title.isEmpty()) title = "Sin título"

            return TgAudio(
                fileId = fileId,
                title = title,
                artist = artist,
                duration = audio.optInt("duration", 0),
                fileSize = audio.optLong("file_size", 0L),
                caption = msg.optString("caption", msg.optString("text", "")),
                fileName = fileName
            )
        }
    }
}

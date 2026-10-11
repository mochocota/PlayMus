package com.example.musica.data

import android.content.Context
import com.example.musica.telegram.TelegramClient
import com.example.musica.telegram.TelegramConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Descarga canciones al teléfono para escucharlas sin internet.
object DownloadRepository {

    private lateinit var context: Context

    // songId -> (bytes descargados, bytes totales)
    private val _progress = MutableStateFlow<Map<Int, Pair<Long, Long>>>(emptyMap())
    val progress: StateFlow<Map<Int, Pair<Long, Long>>> = _progress

    // Contador de cambios (para que la UI reaccione a descargas/borrados)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    // Scope propio: las descargas continúan aunque salgas de la pantalla
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mutex = Mutex()

    fun init(ctx: Context) {
        context = ctx.applicationContext
        dir().mkdirs()
    }

    private fun dir() = File(context.filesDir, "downloads")
    private fun finalFile(id: Int) = File(dir(), "$id.mp3")

    fun downloadedFile(songId: Int): File? {
        val f = finalFile(songId)
        return if (f.exists()) f else null
    }

    fun isDownloaded(songId: Int): Boolean = finalFile(songId).exists()

    fun downloadedCount(): Int =
        dir().listFiles()?.count { it.name.endsWith(".mp3") } ?: 0

    fun totalBytes(): Long =
        dir().listFiles()?.sumOf { it.length() } ?: 0L

    fun delete(songId: Int): Boolean {
        val ok = finalFile(songId).delete()
        bump()
        return ok
    }

    fun deleteAll() {
        dir().listFiles()?.forEach { it.delete() }
        _progress.value = emptyMap()
        bump()
    }

    fun isDownloading(songId: Int): Boolean = _progress.value.containsKey(songId)

    // Lanza la descarga en segundo plano y avisa por callback (en Main)
    fun startDownload(song: Song, onResult: (String) -> Unit = {}) {
        scope.launch {
            val result = download(song)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    private suspend fun download(song: Song): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (isDownloaded(song.id)) {
                bump()
                return@withContext "ok"
            }
            var tmp: File? = null
            try {
                val url = if (song.fromTelegram && song.fileId.isNotEmpty()) {
                    val token = TelegramConfig.token(context)
                    if (token.isBlank()) return@withContext "Sin token del bot"
                    val client = TelegramClient(token)
                    client.fileUrl(client.getFilePath(song.fileId))
                } else {
                    song.url
                }
                if (url.isBlank()) return@withContext "Sin fuente para descargar"

                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                val total = conn.contentLengthLong
                tmp = File(dir(), "${song.id}.part")

                conn.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            done += read
                            _progress.value = _progress.value + (song.id to (done to total))
                        }
                    }
                }
                conn.disconnect()

                if (tmp.length() == 0L) {
                    tmp.delete()
                    return@withContext "El archivo llegó vacío"
                }
                if (!tmp.renameTo(finalFile(song.id))) {
                    tmp.copyTo(finalFile(song.id), overwrite = true)
                    tmp.delete()
                }
                bump()
                "ok"
            } catch (e: Exception) {
                tmp?.delete()
                "Error: ${e.message ?: "desconocido"}"
            } finally {
                _progress.value = _progress.value - song.id
            }
        }
    }

    private fun bump() {
        _version.value = _version.value + 1
    }
}

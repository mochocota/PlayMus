package com.example.musica.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer

// Maneja el ecualizador y el refuerzo de graves de Android,
// enganchados a la sesión de audio del reproductor.
// Guarda los ajustes para que persistan al cerrar la app.
object EqualizerManager {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
    }

    private fun prefs(): SharedPreferences? =
        appCtx?.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)

    val available: Boolean get() = equalizer != null

    // Conecta el ecualizador a la sesión de audio del reproductor
    fun attach(sessionId: Int) {
        release()
        try {
            equalizer = Equalizer(0, sessionId)
            bassBoost = BassBoost(0, sessionId)
            applySaved()
        } catch (_: Exception) {
            // El dispositivo no soporta ecualizador
            equalizer = null
            bassBoost = null
        }
    }

    private fun applySaved() {
        val p = prefs() ?: return
        val on = p.getBoolean("enabled", false)
        try {
            equalizer?.enabled = on
            bassBoost?.enabled = on
            if (on) {
                bassBoost?.setStrength(p.getInt("bass", 0).toShort())
                val n = equalizer?.numberOfBands?.toInt() ?: 0
                for (b in 0 until n) {
                    val v = p.getInt("band_$b", Int.MIN_VALUE)
                    if (v != Int.MIN_VALUE) equalizer?.setBandLevel(b.toShort(), v.toShort())
                }
            }
        } catch (_: Exception) {}
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
    }

    // ---------- Bandas ----------
    fun bandCount(): Int = try {
        equalizer?.numberOfBands?.toInt() ?: 0
    } catch (_: Exception) { 0 }

    fun bandRange(): ShortArray = try {
        equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
    } catch (_: Exception) { shortArrayOf(-1500, 1500) }

    fun bandLevel(band: Int): Int = try {
        equalizer?.getBandLevel(band.toShort())?.toInt() ?: 0
    } catch (_: Exception) { 0 }

    fun setBandLevel(band: Int, milliBel: Int) {
        try {
            equalizer?.setBandLevel(band.toShort(), milliBel.toShort())
            prefs()?.edit()?.putInt("band_$band", milliBel)?.putInt("preset", -1)?.apply()
        } catch (_: Exception) {}
    }

    fun bandFreq(band: Int): Int = try {
        equalizer?.getCenterFreq(band.toShort()) ?: 0
    } catch (_: Exception) { 0 }

    // ---------- Activación global ----------
    fun isEnabled(): Boolean = try {
        equalizer?.enabled == true
    } catch (_: Exception) { false }

    fun setEnabled(on: Boolean) {
        try {
            equalizer?.enabled = on
            bassBoost?.enabled = on
            prefs()?.edit()?.putBoolean("enabled", on)?.apply()
        } catch (_: Exception) {}
    }

    // ---------- Presets ----------
    fun presetCount(): Int = try {
        equalizer?.numberOfPresets?.toInt() ?: 0
    } catch (_: Exception) { 0 }

    fun presetName(i: Int): String = try {
        equalizer?.getPresetName(i.toShort()) ?: ""
    } catch (_: Exception) { "" }

    fun currentPreset(): Int = try {
        prefs()?.getInt("preset", -1) ?: -1
    } catch (_: Exception) { -1 }

    fun usePreset(i: Int) {
        try {
            equalizer?.usePreset(i.toShort())
            val p = prefs() ?: return
            p.edit().putInt("preset", i).apply()
            val n = equalizer?.numberOfBands?.toInt() ?: 0
            for (b in 0 until n) {
                p.edit().putInt("band_$b", equalizer?.getBandLevel(b.toShort())?.toInt() ?: 0).apply()
            }
        } catch (_: Exception) {}
    }

    // ---------- Bass Boost ----------
    fun bassStrength(): Int = try {
        prefs()?.getInt("bass", 0) ?: 0
    } catch (_: Exception) { 0 }

    fun setBassStrength(s: Int) {
        try {
            bassBoost?.setStrength(s.toShort())
            prefs()?.edit()?.putInt("bass", s)?.apply()
        } catch (_: Exception) {}
    }
}

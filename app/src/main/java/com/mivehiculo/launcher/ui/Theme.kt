package com.mivehiculo.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal class Scheme(
    val bgTop: Color,
    val bgBottom: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val track: Color,
    val chip: Color,
    val accent: Color,
    val accentDark: Color,
    val accentSoft: Color,
    val shadow: Color,
    val glow: Color,
    val overlay: Color,
    val overlayStrong: Color,
)

/** Modo Día (el diseño aprobado, sin cambios). */
private val LightScheme = Scheme(
    bgTop = Color(0xFFF8F6F3),
    bgBottom = Color(0xFFE8E4DE),
    card = Color(0xFFFFFFFF),
    text = Color(0xFF1B1D21),
    muted = Color(0xFF6A6F78),
    track = Color(0xFFE9E5DF),
    chip = Color(0xFFF1EEE9),
    accent = Color(0xFFD9772B),       // cobre / naranja cálido
    accentDark = Color(0xFFB85F1C),
    accentSoft = Color(0xFFFCE9D9),
    shadow = Color(0x26000000),
    glow = Color(0x99FFFFFF),
    overlay = Color(0xE6FFFFFF),
    overlayStrong = Color(0xF2FFFFFF),
)

/** Modo Noche: mismo diseño, fondo casi negro, tarjetas grises, el cobre se mantiene. */
private val DarkScheme = Scheme(
    bgTop = Color(0xFF161A20),
    bgBottom = Color(0xFF0A0C0F),
    card = Color(0xFF1D222A),
    text = Color(0xFFF2F4F7),
    muted = Color(0xFF9BA4B0),
    track = Color(0xFF303742),
    chip = Color(0xFF2A313B),
    accent = Color(0xFFD9772B),       // el mismo cobre
    accentDark = Color(0xFFEE944F),   // versión más clara para que se lea sobre fondo oscuro
    accentSoft = Color(0xFF3B2A1E),
    shadow = Color(0x66000000),
    glow = Color(0x12FFFFFF),
    overlay = Color(0xE61D222A),
    overlayStrong = Color(0xF21D222A),
)

/**
 * Colores de toda la app. Cada propiedad lee un estado de Compose: al cambiar el tema,
 * la pantalla se redibuja sola sin tener que tocar ningún otro archivo.
 */
object Palette {
    private var dark by mutableStateOf(false)

    val IsDark: Boolean get() = dark

    fun setDark(value: Boolean) {
        dark = value
    }

    private val s: Scheme get() = if (dark) DarkScheme else LightScheme

    val BgTop: Color get() = s.bgTop
    val BgBottom: Color get() = s.bgBottom
    val Card: Color get() = s.card
    val Text: Color get() = s.text
    val Muted: Color get() = s.muted
    val Track: Color get() = s.track
    val Chip: Color get() = s.chip
    val Accent: Color get() = s.accent
    val AccentDark: Color get() = s.accentDark
    val AccentSoft: Color get() = s.accentSoft
    val Shadow: Color get() = s.shadow
    val Glow: Color get() = s.glow
    val Overlay: Color get() = s.overlay
    val OverlayStrong: Color get() = s.overlayStrong
}

/** Fondo con degradado vertical y un brillo suave arriba a la izquierda (se dibuja una sola vez). */
fun Modifier.screenBackground(): Modifier = this.drawBehind {
    drawRect(Brush.verticalGradient(listOf(Palette.BgTop, Palette.BgBottom)))
    drawRect(
        Brush.radialGradient(
            colors = listOf(Palette.Glow, Color.Transparent),
            center = Offset(size.width * 0.2f, 0f),
            radius = size.width * 0.7f,
        )
    )
}

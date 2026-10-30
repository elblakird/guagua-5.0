package com.mivehiculo.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class VoiceState { Idle, Listening, Thinking, Speaking }

private fun Modifier.panel(s: Float, shape: Shape): Modifier =
    this.shadow((10 * s).dp, shape, clip = false, ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
        .clip(shape)
        .background(Palette.Card)

@Composable
fun AssistantScreen(
    state: VoiceState,
    heard: String,
    reply: String,
    level: () -> Float,
    onMic: () -> Unit,
    onBack: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().screenBackground()) {
        val s = (maxHeight.value / 600f).coerceIn(0.6f, 2.5f)

        Column(Modifier.fillMaxSize().padding(horizontal = (28 * s).dp, vertical = (20 * s).dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size((64 * s).dp).panel(s, CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", color = Palette.Text, fontSize = (30 * s).sp)
                }
                Spacer(Modifier.width((20 * s).dp))
                Column {
                    Text("Jarvis", color = Palette.Text, fontSize = (34 * s).sp, fontWeight = FontWeight.Medium)
                    Text("Asistente del coche", color = Palette.Muted, fontSize = (18 * s).sp)
                }
            }

            Spacer(Modifier.height((12 * s).dp))

            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy((18 * s).dp),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MicButton(s, state, level, onMic)
                    Spacer(Modifier.height((14 * s).dp))
                    Text(
                        when (state) {
                            VoiceState.Idle -> "Presiona para hablar"
                            VoiceState.Listening -> "Escuchando… toca para terminar"
                            VoiceState.Thinking -> "Pensando… toca para cancelar"
                            VoiceState.Speaking -> "Hablando… toca para detener"
                        },
                        color = if (state == VoiceState.Idle) Palette.Muted else Palette.Accent,
                        fontSize = (24 * s).sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Column(
                    Modifier.weight(1.3f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy((16 * s).dp),
                ) {
                    TextCard(s, "Tú", heard, "Aquí verás lo que dices", false, Modifier.weight(1f).fillMaxWidth())
                    TextCard(s, "Jarvis", reply, "Aquí aparecerá la respuesta", true, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

/** Círculo central con ondas. Las ondas crecen con el volumen de tu voz (sin animaciones pesadas). */
@Composable
private fun MicButton(s: Float, state: VoiceState, level: () -> Float, onClick: () -> Unit) {
    val box = (260 * s).dp
    Box(
        Modifier.size(box).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension / 2f
            val active = state == VoiceState.Listening || state == VoiceState.Speaking
            val l = when (state) {
                VoiceState.Listening -> level()
                VoiceState.Speaking -> 0.5f
                else -> 0f
            }
            for (i in 3 downTo 1) {
                val r = base * (0.52f + 0.11f * i + 0.05f * l * i)
                val alpha = ((if (active) 0.20f else 0.12f) - (i - 1) * 0.045f).coerceAtLeast(0.03f)
                drawCircle(Palette.Accent.copy(alpha = alpha), radius = r, center = center)
            }
            drawCircle(Palette.Accent, radius = base * 0.46f, center = center)
        }
        GlyphIcon(Glyph.Mic, Color.White, (92 * s).dp)
    }
}

@Composable
private fun TextCard(s: Float, label: String, text: String, placeholder: String, accent: Boolean, modifier: Modifier) {
    val empty = text.isEmpty()
    Column(modifier.panel(s, RoundedCornerShape((32 * s).dp)).padding((24 * s).dp)) {
        Text(
            label,
            color = if (accent) Palette.Accent else Palette.Muted,
            fontSize = (20 * s).sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height((8 * s).dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                if (empty) placeholder else text,
                color = if (empty) Palette.Muted else Palette.Text,
                fontSize = (28 * s).sp,
                fontWeight = if (empty) FontWeight.Normal else FontWeight.Medium,
            )
        }
    }
}

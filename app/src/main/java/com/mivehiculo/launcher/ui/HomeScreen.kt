package com.mivehiculo.launcher.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mivehiculo.launcher.Config
import com.mivehiculo.launcher.R
import com.mivehiculo.launcher.data.MediaState
import kotlinx.coroutines.delay

data class HomeActions(
    val openRadio: () -> Unit,
    val openMusic: () -> Unit,
    val openPhone: () -> Unit,
    val openApps: () -> Unit,
    val openAndroidAuto: () -> Unit,
    val openNavigation: () -> Unit,
    val openSettings: () -> Unit,
    val mediaPrev: () -> Unit,
    val mediaPlayPause: () -> Unit,
    val mediaNext: () -> Unit,
    val openNotificationAccess: () -> Unit,
    val openMaintenance: () -> Unit,
    val openRearCamera: () -> Unit,
    val pickRearCamera: () -> Unit,
    val pickRadio: () -> Unit,
    val openAssistant: () -> Unit,
)

@Composable
fun HomeScreen(
    time: String,
    date: String,
    weather: String,
    feels: String,
    media: MediaState,
    speedKmh: String,
    odometerKm: String,
    actions: HomeActions,
) {
    BoxWithConstraints(Modifier.fillMaxSize().screenBackground()) {
        val s = (maxHeight.value / 600f).coerceIn(0.6f, 2.5f)

        Column(Modifier.fillMaxSize()) {
            TopBar(s, time, date)

            Row(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = (24 * s).dp),
                horizontalArrangement = Arrangement.spacedBy((18 * s).dp),
            ) {
                Column(
                    Modifier.weight(1.15f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy((16 * s).dp),
                ) {
                    MusicCard(s, media, actions, Modifier.weight(1f).fillMaxWidth())
                    AutoCard(s, actions.openAndroidAuto, Modifier.fillMaxWidth().height((96 * s).dp))
                }
                VehicleCard(s, actions.openMaintenance, speedKmh, odometerKm, Modifier.weight(1f).fillMaxHeight())
                MapCard(s, weather, feels, actions.openNavigation, Modifier.weight(1.15f).fillMaxHeight())
            }

            Spacer(Modifier.height((16 * s).dp))
            Dock(s, actions)
        }
    }
}

@Composable
private fun TopBar(s: Float, time: String, date: String) {
    Row(
        Modifier.fillMaxWidth().height((80 * s).dp).padding(horizontal = (32 * s).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                Config.BIENVENIDA,
                color = Palette.Text,
                fontSize = (24 * s).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(date, color = Palette.Muted, fontSize = (15 * s).sp, maxLines = 1)
        }
        Text(time, color = Palette.Text, fontSize = (56 * s).sp, fontWeight = FontWeight.Light)
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy((22 * s).dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.Brightness, Palette.Muted, (30 * s).dp)
            GlyphIcon(Glyph.Volume, Palette.Muted, (30 * s).dp)
            GlyphIcon(Glyph.Wifi, Palette.Accent, (30 * s).dp)
            GlyphIcon(Glyph.Bluetooth, Palette.Accent, (30 * s).dp)
        }
    }
}

@Composable
private fun FloatingCard(
    s: Float,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape((32 * s).dp)
    Box(
        modifier
            .shadow((10 * s).dp, shape, clip = false, ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
            .clip(shape)
            .background(Palette.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
private fun RoundButton(
    glyph: Glyph,
    diameter: Dp,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(diameter).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, foreground, diameter * 0.5f)
    }
}

@Composable
private fun MusicCard(s: Float, media: MediaState, actions: HomeActions, modifier: Modifier) {
    val title = if (media.hasSession) (media.title ?: "Sin título") else "Música detenida"
    val artist = when {
        media.hasSession -> media.artist ?: ""
        !media.hasAccess -> "Activa el acceso a notificaciones"
        else -> "Abre tu app de música"
    }

    FloatingCard(s, modifier, if (media.hasAccess) null else actions.openNotificationAccess) {
        Column(
            Modifier.fillMaxSize().padding((20 * s).dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size((84 * s).dp)
                        .clip(RoundedCornerShape((20 * s).dp))
                        .background(Brush.linearGradient(listOf(Palette.Accent, Color(0xFF8E3F10)))),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = media.art
                    if (art != null) {
                        Image(
                            bitmap = art,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("🎵", fontSize = (38 * s).sp)
                    }
                }
                Spacer(Modifier.width((18 * s).dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Palette.Text,
                        fontSize = (28 * s).sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        artist,
                        color = Palette.Muted,
                        fontSize = (20 * s).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            ProgressBlock(s, media)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Glyph.Prev, (58 * s).dp, Palette.Chip, Palette.Text, actions.mediaPrev)
                RoundButton(
                    if (media.playing) Glyph.Pause else Glyph.Play,
                    (70 * s).dp,
                    Palette.Accent,
                    Color.White,
                    actions.mediaPlayPause,
                )
                RoundButton(Glyph.Next, (58 * s).dp, Palette.Chip, Palette.Text, actions.mediaNext)
            }
        }
    }
}

@Composable
private fun ProgressBlock(s: Float, media: MediaState) {
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(media.playing, media.stateTimeMs) {
        now = SystemClock.elapsedRealtime()
        if (media.playing) {
            while (true) {
                delay(1000L)
                now = SystemClock.elapsedRealtime()
            }
        }
    }

    val pos = media.positionAt(now)
    val fraction = if (media.durationMs > 0L) (pos.toFloat() / media.durationMs).coerceIn(0f, 1f) else 0f

    Column {
        Box(
            Modifier.fillMaxWidth().height((6 * s).dp).clip(CircleShape).background(Palette.Track),
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Palette.Accent))
        }
        Spacer(Modifier.height((6 * s).dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(pos), color = Palette.Muted, fontSize = (15 * s).sp)
            Text(
                if (media.durationMs > 0L) formatTime(media.durationMs) else "--:--",
                color = Palette.Muted,
                fontSize = (15 * s).sp,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

@Composable
private fun AutoCard(s: Float, onClick: () -> Unit, modifier: Modifier) {
    FloatingCard(s, modifier, onClick) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = (20 * s).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size((58 * s).dp).clip(CircleShape).background(Palette.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text("📱", fontSize = (28 * s).sp)
            }
            Spacer(Modifier.width((16 * s).dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Android Auto",
                    color = Palette.Text,
                    fontSize = (22 * s).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text("Conectar teléfono", color = Palette.Muted, fontSize = (16 * s).sp, maxLines = 1)
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Palette.Accent)
                    .padding(horizontal = (20 * s).dp, vertical = (10 * s).dp),
            ) {
                Text("Conectar", color = Color.White, fontSize = (18 * s).sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun VehicleCard(
    s: Float,
    onClick: () -> Unit,
    speedKmh: String,
    odometerKm: String,
    modifier: Modifier,
) {
    FloatingCard(s, modifier, onClick) {
        Column(
            Modifier.fillMaxSize().padding((22 * s).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                Config.VEHICULO,
                color = Palette.Text,
                fontSize = (24 * s).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height((4 * s).dp))
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.santa_fe),
                contentDescription = Config.VEHICULO,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Spacer(Modifier.height((8 * s).dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(speedKmh, color = Palette.Accent, fontSize = (72 * s).sp, fontWeight = FontWeight.Medium)
                        Text(
                            " km/h",
                            Modifier.padding(bottom = (14 * s).dp),
                            color = Palette.Muted,
                            fontSize = (20 * s).sp,
                        )
                    }
                }
                Box(Modifier.width(1.dp).height((56 * s).dp).background(Palette.Track))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(odometerKm, color = Palette.Text, fontSize = (34 * s).sp, fontWeight = FontWeight.Medium)
                    Text("km recorridos", color = Palette.Muted, fontSize = (16 * s).sp)
                }
            }
        }
    }
}

@Composable
private fun MapCard(s: Float, weather: String, feels: String, onClick: () -> Unit, modifier: Modifier) {
    FloatingCard(s, modifier, onClick) {
        MapIllustration(Modifier.fillMaxSize())

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding((14 * s).dp)
                .clip(CircleShape)
                .background(Palette.Overlay)
                .padding(horizontal = (14 * s).dp, vertical = (7 * s).dp),
        ) {
            Column {
                Text(weather, color = Palette.Muted, fontSize = (15 * s).sp, fontWeight = FontWeight.Medium)
                if (feels.isNotEmpty()) {
                    Text(feels, color = Palette.Muted, fontSize = (13 * s).sp)
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding((14 * s).dp)
                .clip(CircleShape)
                .background(Palette.OverlayStrong)
                .padding(horizontal = (16 * s).dp, vertical = (9 * s).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size((12 * s).dp).clip(CircleShape).background(Palette.Accent))
            Spacer(Modifier.width((10 * s).dp))
            Text("Google Maps", color = Palette.Text, fontSize = (20 * s).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Dock(s: Float, actions: HomeActions) {
    val shape = RoundedCornerShape(topStart = (32 * s).dp, topEnd = (32 * s).dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow((14 * s).dp, shape, clip = false, ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
            .background(Palette.Card, shape)
            .padding(horizontal = (16 * s).dp, vertical = (14 * s).dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        DockItem(s, "Radio", false, actions.openRadio, actions.pickRadio) { Emoji("📻", s) }
        DockItem(s, "Música", true, actions.openMusic) { Emoji("🎵", s) }
        DockItem(s, "Teléfono", false, actions.openPhone) { Emoji("📞", s) }
        DockItem(s, "Apps", false, actions.openApps) { GlyphIcon(Glyph.Apps, Palette.Text, (30 * s).dp) }
        DockItem(s, "Android Auto", false, actions.openAndroidAuto) { Emoji("📱", s) }
        DockItem(s, "Navegación", false, actions.openNavigation) { Emoji("🧭", s) }
        DockItem(s, "Cámara trasera", false, actions.openRearCamera, actions.pickRearCamera) { Emoji("📷", s) }
        DockItem(s, "Jarvis", false, actions.openAssistant) { Emoji("🎤", s) }
        DockItem(s, "Ajustes", false, actions.openSettings) { Emoji("⚙️", s) }
    }
}

@Composable
private fun Emoji(text: String, s: Float) {
    Text(text, fontSize = (30 * s).sp)
}

@Composable
private fun DockItem(
    s: Float,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape((20 * s).dp))
            .then(
                if (onLongClick != null) {
                    Modifier.pointerInput(onClick, onLongClick) {
                        detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
                    }
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(horizontal = (10 * s).dp, vertical = (4 * s).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size((64 * s).dp)
                .clip(CircleShape)
                .background(if (active) Palette.AccentSoft else Palette.Chip),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.height((6 * s).dp))
        Text(
            label,
            color = if (active) Palette.AccentDark else Palette.Text,
            fontSize = (17 * s).sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

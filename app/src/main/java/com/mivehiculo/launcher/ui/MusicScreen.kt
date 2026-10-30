package com.mivehiculo.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mivehiculo.launcher.data.Folder
import com.mivehiculo.launcher.data.MusicLibrary
import com.mivehiculo.launcher.data.Track
import kotlinx.coroutines.delay

enum class MusicPhase { NoPermission, Loading, Empty, Ready }

data class NowPlaying(
    val path: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val art: ImageBitmap? = null,
    val playing: Boolean = false,
    val durationMs: Long = 0L,
)

class MusicActions(
    val back: () -> Unit,
    val rescan: () -> Unit,
    val grant: () -> Unit,
    val openFolder: (String) -> Unit,
    val playSong: (Int) -> Unit,
    val prev: () -> Unit,
    val playPause: () -> Unit,
    val next: () -> Unit,
    val seek: (Float) -> Unit,
)

private fun Modifier.panel(s: Float, shape: Shape): Modifier =
    this.shadow((10 * s).dp, shape, clip = false, ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
        .clip(shape)
        .background(Palette.Card)

@Composable
fun MusicScreen(
    phase: MusicPhase,
    folderName: String?,
    folders: List<Folder>,
    songs: List<Track>,
    now: NowPlaying,
    getPosition: () -> Long,
    a: MusicActions,
) {
    BoxWithConstraints(Modifier.fillMaxSize().screenBackground()) {
        val s = (maxHeight.value / 600f).coerceIn(0.6f, 2.5f)

        Column(Modifier.fillMaxSize().padding(horizontal = (28 * s).dp, vertical = (20 * s).dp)) {
            // ---- Cabecera
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size((64 * s).dp).panel(s, CircleShape).clickable(onClick = a.back),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", color = Palette.Text, fontSize = (30 * s).sp)
                }
                Spacer(Modifier.width((20 * s).dp))
                Column(Modifier.weight(1f)) {
                    Text("Música", color = Palette.Text, fontSize = (34 * s).sp, fontWeight = FontWeight.Medium)
                    Text(
                        folderName ?: "Carpetas del USB",
                        color = Palette.Muted,
                        fontSize = (18 * s).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Palette.Chip)
                        .clickable(onClick = a.rescan)
                        .padding(horizontal = (20 * s).dp, vertical = (10 * s).dp),
                ) {
                    Text("Buscar de nuevo", color = Palette.Text, fontSize = (18 * s).sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height((14 * s).dp))

            // ---- Lista de carpetas o canciones
            Box(Modifier.weight(1f).fillMaxWidth().panel(s, RoundedCornerShape((32 * s).dp))) {
                when (phase) {
                    MusicPhase.Loading -> Message(
                        s, "Buscando música…",
                        "Puede tardar unos segundos si el USB tiene muchas canciones.", null, null,
                    )
                    MusicPhase.NoPermission -> Message(
                        s, "Falta el permiso de almacenamiento",
                        "Lo necesito para leer la música de tu USB.", "Dar permiso", a.grant,
                    )
                    MusicPhase.Empty -> Message(
                        s, "No encontré música",
                        "Conecta tu USB con archivos MP3, FLAC, M4A… y toca «Buscar de nuevo».", null, null,
                    )
                    MusicPhase.Ready ->
                        if (folderName == null) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(folders, key = { it.path }) { f ->
                                    ListRow(s, if (f.path == MusicLibrary.ALL) "🎶" else "📁", f.name, "${f.count} canciones", false) {
                                        a.openFolder(f.path)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                itemsIndexed(songs, key = { _, t -> t.path }) { index, t ->
                                    ListRow(s, "🎵", t.title, t.folderName, t.path == now.path) { a.playSong(index) }
                                }
                            }
                        }
                }
            }

            Spacer(Modifier.height((14 * s).dp))

            // ---- Controles
            PlayerBar(s, now, getPosition, a, Modifier.fillMaxWidth().height((168 * s).dp))
        }
    }
}

@Composable
private fun Message(s: Float, title: String, body: String, button: String?, onButton: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().padding((24 * s).dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Palette.Text, fontSize = (28 * s).sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height((8 * s).dp))
        Text(body, color = Palette.Muted, fontSize = (20 * s).sp, textAlign = TextAlign.Center)
        if (button != null && onButton != null) {
            Spacer(Modifier.height((16 * s).dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape((22 * s).dp))
                    .background(Palette.Accent)
                    .clickable(onClick = onButton)
                    .padding(horizontal = (28 * s).dp, vertical = (14 * s).dp),
            ) {
                Text(button, color = Color.White, fontSize = (22 * s).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ListRow(s: Float, icon: String, title: String, subtitle: String, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = (22 * s).dp, vertical = (10 * s).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size((46 * s).dp)
                    .clip(RoundedCornerShape((14 * s).dp))
                    .background(if (active) Palette.AccentSoft else Palette.Chip),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontSize = (22 * s).sp)
            }
            Spacer(Modifier.width((16 * s).dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (active) Palette.Accent else Palette.Text,
                    fontSize = (22 * s).sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, color = Palette.Muted, fontSize = (16 * s).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = (22 * s).dp).height(1.dp).background(Palette.Track))
    }
}

@Composable
private fun PlayerBar(s: Float, now: NowPlaying, getPosition: () -> Long, a: MusicActions, modifier: Modifier) {
    Column(
        modifier.panel(s, RoundedCornerShape((32 * s).dp)).padding(horizontal = (22 * s).dp, vertical = (14 * s).dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size((76 * s).dp)
                    .clip(RoundedCornerShape((18 * s).dp))
                    .background(Brush.linearGradient(listOf(Palette.Accent, Color(0xFF8E3F10)))),
                contentAlignment = Alignment.Center,
            ) {
                val art = now.art
                if (art != null) {
                    Image(
                        bitmap = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("🎵", fontSize = (34 * s).sp)
                }
            }
            Spacer(Modifier.width((18 * s).dp))
            Column(Modifier.weight(1f)) {
                Text(
                    now.title ?: "Nada reproduciéndose",
                    color = Palette.Text,
                    fontSize = (26 * s).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (now.path == null) "Elige una canción de la lista" else (now.artist ?: ""),
                    color = Palette.Muted,
                    fontSize = (19 * s).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy((14 * s).dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Glyph.Prev, (56 * s).dp, Palette.Chip, Palette.Text, a.prev)
                CircleButton(
                    if (now.playing) Glyph.Pause else Glyph.Play,
                    (70 * s).dp,
                    Palette.Accent,
                    Color.White,
                    a.playPause,
                )
                CircleButton(Glyph.Next, (56 * s).dp, Palette.Chip, Palette.Text, a.next)
            }
        }
        ProgressBlock(s, now, getPosition, a.seek)
    }
}

@Composable
private fun CircleButton(glyph: Glyph, diameter: Dp, background: Color, foreground: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(diameter).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, foreground, diameter * 0.5f)
    }
}

/** Barra de progreso: avanza sola cada segundo mientras suena y se puede tocar para saltar a otro punto. */
@Composable
private fun ProgressBlock(s: Float, now: NowPlaying, getPosition: () -> Long, onSeek: (Float) -> Unit) {
    var pos by remember { mutableStateOf(0L) }
    LaunchedEffect(now.playing, now.path) {
        pos = getPosition()
        if (now.playing) {
            while (true) {
                delay(1000L)
                pos = getPosition()
            }
        }
    }
    val fraction = if (now.durationMs > 0L) (pos.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height((28 * s).dp)
                .pointerInput(now.durationMs) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        pos = (f * now.durationMs).toLong()
                        onSeek(f)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxWidth().height((8 * s).dp).clip(CircleShape).background(Palette.Track)) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Palette.Accent))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(pos), color = Palette.Muted, fontSize = (15 * s).sp)
            Text(
                if (now.durationMs > 0L) formatTime(now.durationMs) else "--:--",
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

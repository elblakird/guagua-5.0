package com.mivehiculo.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mivehiculo.launcher.Config
import com.mivehiculo.launcher.data.KmFormat
import com.mivehiculo.launcher.data.MaintenanceData

enum class Overlay { None, CurrentKm, LastKm, Confirm }

class MaintenanceActions(
    val back: () -> Unit,
    val pickInterval: (Int) -> Unit,
    val requestChange: () -> Unit,
    val openCurrentKm: () -> Unit,
    val openLastKm: () -> Unit,
    val dismiss: () -> Unit,
    val confirmChange: () -> Unit,
    val saveCurrentKm: (Int) -> Unit,
    val saveLastKm: (Int) -> Unit,
    val setThemeMode: (ThemeMode) -> Unit,
)

/** Tarjeta blanca flotante (mismo estilo que el Home). */
private fun Modifier.card(s: Float, shape: Shape, color: Color = Palette.Card): Modifier =
    this.shadow((10 * s).dp, shape, clip = false, ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
        .clip(shape)
        .background(color)

@Composable
fun MaintenanceScreen(data: MaintenanceData, overlay: Overlay, themeMode: ThemeMode, a: MaintenanceActions) {
    BoxWithConstraints(Modifier.fillMaxSize().screenBackground()) {
        val s = (maxHeight.value / 600f).coerceIn(0.6f, 2.5f)

        Column(
            Modifier.fillMaxSize().padding(horizontal = (28 * s).dp, vertical = (20 * s).dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size((64 * s).dp).card(s, CircleShape).clickable(onClick = a.back),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", color = Palette.Text, fontSize = (30 * s).sp)
                }
                Spacer(Modifier.width((20 * s).dp))
                Column {
                    Text("Mantenimiento", color = Palette.Text, fontSize = (34 * s).sp, fontWeight = FontWeight.Medium)
                    Text(Config.VEHICULO, color = Palette.Muted, fontSize = (18 * s).sp)
                }
                Spacer(Modifier.weight(1f))
                ThemeSwitch(s, themeMode, a.setThemeMode)
            }

            Spacer(Modifier.height((18 * s).dp))

            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy((18 * s).dp),
            ) {
                OilCard(s, data, a, Modifier.weight(1.5f).fillMaxHeight())

                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy((16 * s).dp),
                ) {
                    IntervalCard(s, data.intervalKm, a.pickInterval, Modifier.weight(1f).fillMaxWidth())
                    BigButton(s, "Hice el cambio hoy", Palette.Accent, Color.White, 96f, a.requestChange)
                    BigButton(s, "Actualizar kilometraje", Palette.Card, Palette.Text, 76f, a.openCurrentKm)
                }
            }
        }

        when (overlay) {
            Overlay.CurrentKm -> ModalLayer(a.dismiss) {
                KmPad(
                    s,
                    title = "Kilometraje actual",
                    hint = if (data.currentKm > 0) "Ahora: ${KmFormat.km(data.currentKm)} km" else "Escribe lo que marca tu tablero",
                    onSave = a.saveCurrentKm,
                    onCancel = a.dismiss,
                )
            }
            Overlay.LastKm -> ModalLayer(a.dismiss) {
                KmPad(
                    s,
                    title = "Km del último cambio de aceite",
                    hint = data.lastKm?.let { "Ahora: ${KmFormat.km(it)} km" } ?: "Sin cambio registrado",
                    onSave = a.saveLastKm,
                    onCancel = a.dismiss,
                )
            }
            Overlay.Confirm -> ModalLayer(a.dismiss) { ConfirmCard(s, data, a) }
            Overlay.None -> Unit
        }
    }
}

/* ---------- Tema: Auto / Día / Noche ---------- */

@Composable
private fun ThemeSwitch(s: Float, mode: ThemeMode, onPick: (ThemeMode) -> Unit) {
    Row(
        Modifier.card(s, RoundedCornerShape((26 * s).dp)).padding((6 * s).dp),
        horizontalArrangement = Arrangement.spacedBy((6 * s).dp),
    ) {
        for ((m, label) in listOf(ThemeMode.AUTO to "Auto", ThemeMode.DAY to "Día", ThemeMode.NIGHT to "Noche")) {
            val selected = m == mode
            Box(
                Modifier
                    .width((92 * s).dp)
                    .height((46 * s).dp)
                    .clip(RoundedCornerShape((20 * s).dp))
                    .background(if (selected) Palette.Accent else Palette.Chip)
                    .clickable { onPick(m) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Color.White else Palette.Text,
                    fontSize = (20 * s).sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/* ---------- Tarjeta principal ---------- */

@Composable
private fun OilCard(s: Float, d: MaintenanceData, a: MaintenanceActions, modifier: Modifier) {
    val remaining = d.remainingKm
    val used = d.usedKm
    val fraction = if (used != null && d.intervalKm > 0) (used.toFloat() / d.intervalKm).coerceIn(0f, 1f) else 0f

    Column(
        modifier.card(s, RoundedCornerShape((32 * s).dp)).padding((28 * s).dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "Próximo cambio de aceite",
                color = Palette.Muted,
                fontSize = (22 * s).sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height((10 * s).dp))
            when {
                remaining == null -> {
                    Text(
                        "Sin cambio registrado",
                        color = Palette.Text,
                        fontSize = (40 * s).sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height((6 * s).dp))
                    Text(
                        "Toca «Último cambio» e ingresa el kilometraje de tu último cambio de aceite.",
                        color = Palette.Muted,
                        fontSize = (18 * s).sp,
                    )
                }
                remaining >= 0 -> BigNumber(s, "Faltan", KmFormat.km(remaining), Palette.Accent)
                else -> BigNumber(s, "Vencido por", KmFormat.km(-remaining), Palette.AccentDark)
            }
        }

        if (remaining != null) {
            Column {
                Box(Modifier.fillMaxWidth().height((8 * s).dp).clip(CircleShape).background(Palette.Track)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Palette.Accent))
                }
                Spacer(Modifier.height((8 * s).dp))
                d.nextKm?.let {
                    Text(
                        "Próximo cambio a los ${KmFormat.km(it)} km",
                        color = Palette.Muted,
                        fontSize = (18 * s).sp,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy((14 * s).dp)) {
            StatTile(
                s,
                "Kilometraje actual",
                if (d.currentKm > 0) "${KmFormat.km(d.currentKm)} km" else "—",
                null,
                null,
                Modifier.weight(1f),
            )
            StatTile(
                s,
                "Último cambio",
                d.lastKm?.let { "${KmFormat.km(it)} km" } ?: "—",
                when {
                    d.lastKm == null -> "Toca para ingresar"
                    d.lastDateMs != null -> KmFormat.date(d.lastDateMs)
                    else -> "Fecha no registrada"
                },
                a.openLastKm,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BigNumber(s: Float, prefix: String, number: String, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            prefix,
            Modifier.padding(bottom = (14 * s).dp),
            color = Palette.Muted,
            fontSize = (30 * s).sp,
        )
        Spacer(Modifier.width((12 * s).dp))
        Text(number, color = color, fontSize = (84 * s).sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width((10 * s).dp))
        Text(
            "km",
            Modifier.padding(bottom = (14 * s).dp),
            color = Palette.Muted,
            fontSize = (30 * s).sp,
        )
    }
}

@Composable
private fun StatTile(
    s: Float,
    label: String,
    value: String,
    sub: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape((22 * s).dp))
            .background(Palette.Chip)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding((16 * s).dp),
    ) {
        Text(label, color = Palette.Muted, fontSize = (16 * s).sp)
        Text(value, color = Palette.Text, fontSize = (30 * s).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (sub != null) Text(sub, color = Palette.Muted, fontSize = (15 * s).sp, maxLines = 1)
    }
}

/* ---------- Intervalo (chips) y botones ---------- */

@Composable
private fun IntervalCard(s: Float, selected: Int, onPick: (Int) -> Unit, modifier: Modifier) {
    Column(
        modifier.card(s, RoundedCornerShape((32 * s).dp)).padding((20 * s).dp),
        verticalArrangement = Arrangement.spacedBy((12 * s).dp),
    ) {
        Text("Intervalo de cambio", color = Palette.Muted, fontSize = (20 * s).sp, fontWeight = FontWeight.Medium)
        for (pair in listOf(listOf(3000, 5000), listOf(7500, 10000))) {
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy((12 * s).dp),
            ) {
                for (km in pair) {
                    IntervalChip(s, km, km == selected, Modifier.weight(1f).fillMaxHeight()) { onPick(km) }
                }
            }
        }
    }
}

@Composable
private fun IntervalChip(s: Float, km: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape((22 * s).dp))
            .background(if (selected) Palette.Accent else Palette.Chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${KmFormat.km(km)} km",
            color = if (selected) Color.White else Palette.Text,
            fontSize = (22 * s).sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun BigButton(s: Float, label: String, color: Color, textColor: Color, heightDp: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height((heightDp * s).dp)
            .card(s, RoundedCornerShape((28 * s).dp), color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, fontSize = (26 * s).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/* ---------- Ventanas flotantes ---------- */

@Composable
private fun ModalLayer(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x73000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Teclado numérico propio: no depende del teclado del radio (muchos no traen uno). */
@Composable
private fun KmPad(s: Float, title: String, hint: String, onSave: (Int) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("Borrar", "0", "OK"),
    )

    Column(
        Modifier
            .width((440 * s).dp)
            .card(s, RoundedCornerShape((32 * s).dp))
            .clickable(onClick = {})
            .padding((22 * s).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Palette.Text, fontSize = (24 * s).sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(hint, color = Palette.Muted, fontSize = (16 * s).sp)
        Spacer(Modifier.height((8 * s).dp))
        Text(
            if (text.isEmpty()) "0" else KmFormat.km(text.toInt()),
            color = if (text.isEmpty()) Palette.Muted else Palette.Accent,
            fontSize = (56 * s).sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height((8 * s).dp))

        for (row in rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((10 * s).dp)) {
                for (key in row) {
                    val isOk = key == "OK"
                    Box(
                        Modifier
                            .weight(1f)
                            .height((58 * s).dp)
                            .clip(RoundedCornerShape((18 * s).dp))
                            .background(if (isOk) Palette.Accent else Palette.Chip)
                            .clickable {
                                when (key) {
                                    "Borrar" -> text = text.dropLast(1)
                                    "OK" -> if (text.isNotEmpty()) onSave(text.toInt())
                                    else -> if (text.length < 7) text = (text + key).trimStart('0')
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            key,
                            color = if (isOk) Color.White else Palette.Text,
                            fontSize = ((if (key.length > 2) 20 else 30) * s).sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height((10 * s).dp))
        }

        Text(
            "Cancelar",
            Modifier.clip(RoundedCornerShape((14 * s).dp)).clickable(onClick = onCancel).padding(horizontal = (20 * s).dp, vertical = (8 * s).dp),
            color = Palette.Muted,
            fontSize = (20 * s).sp,
        )
    }
}

@Composable
private fun ConfirmCard(s: Float, d: MaintenanceData, a: MaintenanceActions) {
    Column(
        Modifier
            .width((460 * s).dp)
            .card(s, RoundedCornerShape((32 * s).dp))
            .clickable(onClick = {})
            .padding((24 * s).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("¿Registrar cambio de aceite?", color = Palette.Text, fontSize = (28 * s).sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height((10 * s).dp))
        Text(
            "Se guardará con la fecha de hoy (${KmFormat.date(System.currentTimeMillis())}) y ${KmFormat.km(d.currentKm)} km.",
            color = Palette.Muted,
            fontSize = (20 * s).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height((20 * s).dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((12 * s).dp)) {
            ModalButton(s, "Cancelar", Palette.Chip, Palette.Text, Modifier.weight(1f), a.dismiss)
            ModalButton(s, "Registrar", Palette.Accent, Color.White, Modifier.weight(1f), a.confirmChange)
        }
    }
}

@Composable
private fun ModalButton(s: Float, label: String, color: Color, textColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height((62 * s).dp).clip(RoundedCornerShape((22 * s).dp)).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, fontSize = (22 * s).sp, fontWeight = FontWeight.SemiBold)
    }
}

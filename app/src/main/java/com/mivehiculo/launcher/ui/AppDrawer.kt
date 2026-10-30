package com.mivehiculo.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mivehiculo.launcher.data.AppEntry

@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onClose: () -> Unit,
    title: String = "Aplicaciones",
) {
    BoxWithConstraints(Modifier.fillMaxSize().screenBackground()) {
        val s = (maxHeight.value / 600f).coerceIn(0.6f, 2.5f)

        Column(Modifier.fillMaxSize().padding((28 * s).dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .shadow((8 * s).dp, CircleShape, clip = false,
                            ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
                        .size((64 * s).dp)
                        .clip(CircleShape)
                        .background(Palette.Card)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", color = Palette.Text, fontSize = (30 * s).sp)
                }
                Spacer(Modifier.width((20 * s).dp))
                Text(title, color = Palette.Text, fontSize = (34 * s).sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height((20 * s).dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive((150 * s).dp),
                horizontalArrangement = Arrangement.spacedBy((12 * s).dp),
                verticalArrangement = Arrangement.spacedBy((12 * s).dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Column(
                        Modifier
                            .clickable { onLaunch(app) }
                            .padding((8 * s).dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier.size((72 * s).dp),
                        )
                        Spacer(Modifier.height((8 * s).dp))
                        Text(
                            app.label,
                            color = Palette.Text,
                            fontSize = (18 * s).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

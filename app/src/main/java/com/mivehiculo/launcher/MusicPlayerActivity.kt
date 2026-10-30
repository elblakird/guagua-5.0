package com.mivehiculo.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.mivehiculo.launcher.data.MusicLibrary
import com.mivehiculo.launcher.data.MusicPlayer
import com.mivehiculo.launcher.data.Track
import com.mivehiculo.launcher.ui.MusicActions
import com.mivehiculo.launcher.ui.MusicPhase
import com.mivehiculo.launcher.ui.MusicScreen
import com.mivehiculo.launcher.ui.NowPlaying
import com.mivehiculo.launcher.ui.ThemeController
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Reproductor de música local (USB) con Media3 / ExoPlayer.
 * La búsqueda de archivos corre en un hilo aparte; el reproductor sigue sonando al salir de la pantalla.
 */
class MusicPlayerActivity : ComponentActivity() {

    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    private var phase by mutableStateOf(MusicPhase.Loading)
    private var tracks by mutableStateOf<List<Track>>(emptyList())
    private var folderPath by mutableStateOf<String?>(null) // null = lista de carpetas
    private var now by mutableStateOf(NowPlaying())

    private var lastArtData: ByteArray? = null
    private var lastArt: ImageBitmap? = null
    private var errorsInRow = 0

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncNow()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) errorsInRow = 0
        }

        /** Un archivo dañado no detiene la música: salta al siguiente (máximo 3 seguidos). */
        override fun onPlayerError(error: PlaybackException) {
            val p = MusicPlayer.current() ?: return
            if (++errorsInRow <= 3) {
                try {
                    p.seekToNextMediaItem()
                    p.prepare()
                    p.play()
                } catch (e: Exception) {
                    // nada
                }
            } else {
                toast("No se pudieron reproducir esos archivos")
            }
        }
    }

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) scan() else phase = MusicPhase.NoPermission
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeController.load(this)
        ThemeController.sync(this, force = true)

        val actions = MusicActions(
            back = { if (folderPath != null) folderPath = null else finish() },
            rescan = { ensurePermissionThenScan() },
            grant = { requestPermission() },
            openFolder = { folderPath = it },
            playSong = { index -> play(MusicLibrary.songsIn(tracks, folderPath), index) },
            prev = { MusicPlayer.current()?.seekToPreviousMediaItem() },
            playPause = { onPlayPause() },
            next = { MusicPlayer.current()?.seekToNextMediaItem() },
            seek = { fraction -> seekTo(fraction) },
        )

        setContent {
            BackHandler(enabled = folderPath != null) { folderPath = null }
            val list = tracks
            val path = folderPath
            val folders = remember(list) { MusicLibrary.folders(list) }
            val songs = remember(list, path) { MusicLibrary.songsIn(list, path) }
            MusicScreen(
                phase = phase,
                folderName = path?.let { MusicLibrary.folderLabel(it) },
                folders = folders,
                songs = songs,
                now = now,
                getPosition = { MusicPlayer.current()?.currentPosition ?: 0L },
                a = actions,
            )
        }

        ensurePermissionThenScan()
    }

    override fun onStart() {
        super.onStart()
        ThemeController.sync(this, force = true)
        // Si ya hay música sonando (venías del Home), muestra lo que suena.
        MusicPlayer.current()?.let {
            attach(it)
            syncNow()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeController.sync(this)
    }

    override fun onStop() {
        MusicPlayer.current()?.removeListener(listener)
        super.onStop()
    }

    override fun onDestroy() {
        // Si está sonando, sigue sonando; si no, se libera la memoria del reproductor.
        MusicPlayer.releaseIfIdle()
        io.shutdownNow()
        super.onDestroy()
    }

    /* ---------- Permiso y búsqueda ---------- */

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun ensurePermissionThenScan() {
        if (hasPermission()) scan() else requestPermission()
    }

    private fun requestPermission() {
        phase = MusicPhase.NoPermission
        try {
            storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } catch (e: Exception) {
            // si el radio no trae el diálogo de permisos, se queda el aviso en pantalla
        }
    }

    private fun scan() {
        phase = MusicPhase.Loading
        try {
            io.execute {
                val list = try {
                    MusicLibrary.scan(MusicLibrary.roots())
                } catch (e: Exception) {
                    emptyList()
                }
                runOnUiThread {
                    if (!isDestroyed) {
                        tracks = list
                        phase = if (list.isEmpty()) MusicPhase.Empty else MusicPhase.Ready
                    }
                }
            }
        } catch (e: RejectedExecutionException) {
            // la pantalla ya se cerró
        }
    }

    /* ---------- Reproducción ---------- */

    private fun attach(p: Player) {
        p.removeListener(listener)
        p.addListener(listener)
    }

    private fun play(list: List<Track>, index: Int) {
        if (list.isEmpty()) return
        try {
            val p = MusicPlayer.get(this)
            attach(p)
            errorsInRow = 0
            p.setMediaItems(list.map { mediaItem(it) }, index.coerceIn(0, list.lastIndex), 0L)
            p.prepare()
            p.play()
        } catch (e: Exception) {
            toast("No pude reproducir esa canción")
        }
    }

    private fun mediaItem(t: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(t.path)
            .setUri(Uri.fromFile(File(t.path)))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.folderName).build())
            .build()

    private fun onPlayPause() {
        val p = MusicPlayer.current()
        if (p == null || p.currentMediaItem == null) {
            // Nada cargado: empieza por la primera canción de lo que estás viendo.
            val list = MusicLibrary.songsIn(tracks, folderPath ?: MusicLibrary.ALL)
            play(list, 0)
        } else if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
    }

    private fun seekTo(fraction: Float) {
        val p = MusicPlayer.current() ?: return
        val d = p.duration
        if (d != C.TIME_UNSET && d > 0) p.seekTo((fraction * d).toLong())
    }

    /** Copia el estado del reproductor a la pantalla. */
    private fun syncNow() {
        val p = MusicPlayer.current()
        val item = p?.currentMediaItem
        if (p == null || item == null) {
            now = NowPlaying()
            return
        }
        val md = p.mediaMetadata
        val duration = p.duration
        now = NowPlaying(
            path = item.mediaId,
            title = md.title?.toString(),
            artist = (md.artist ?: md.albumArtist)?.toString(),
            art = artOf(md.artworkData),
            playing = p.isPlaying,
            durationMs = if (duration != C.TIME_UNSET && duration > 0) duration else 0L,
        )
    }

    /** Carátula incrustada en el archivo, reducida (máx. ~512 px) para ahorrar RAM. */
    private fun artOf(data: ByteArray?): ImageBitmap? {
        if (data == null) {
            lastArtData = null
            lastArt = null
            return null
        }
        if (data === lastArtData) return lastArt
        val img = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
            BitmapFactory.decodeByteArray(
                data, 0, data.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )?.asImageBitmap()
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
        lastArtData = data
        lastArt = img
        return img
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

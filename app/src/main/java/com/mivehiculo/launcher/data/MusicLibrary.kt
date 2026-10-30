package com.mivehiculo.launcher.data

import com.mivehiculo.launcher.Config
import java.io.File

data class Track(val path: String, val title: String, val folderPath: String, val folderName: String)

data class Folder(val path: String, val name: String, val count: Int)

/**
 * Busca música en el USB (y en /Music interno). Se ejecuta SIEMPRE en un hilo aparte.
 * Es liviano: profundidad y cantidad de archivos limitadas, sin leer etiquetas de cada canción.
 */
object MusicLibrary {
    /** Identificador de la "carpeta" especial que junta todas las canciones. */
    const val ALL = "*todas*"

    private val EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")
    private val SKIP_DIRS = setOf("android", "lost.dir", "system volume information", "\$recycle.bin", "recycler")
    private const val MAX_FILES = 6000
    private const val MAX_DEPTH = 8

    /** Carpetas raíz donde buscar: las de Config.RUTAS_MUSICA + cada volumen que aparezca en /storage. */
    fun roots(): List<File> {
        val found = LinkedHashMap<String, File>() // ruta canónica -> carpeta (evita escanear dos veces el mismo USB)

        fun add(dir: File) {
            try {
                if (!dir.isDirectory || !dir.canRead()) return
                val canon = dir.canonicalPath
                if (found.keys.none { canon == it || canon.startsWith("$it/") }) found[canon] = dir
            } catch (e: Exception) {
                // carpeta inaccesible: se ignora
            }
        }

        for (p in Config.RUTAS_MUSICA) add(File(p))
        for (parent in listOf("/storage", "/mnt/usb_storage", "/mnt/usbhost")) {
            val kids = try {
                File(parent).listFiles()
            } catch (e: Exception) {
                null
            } ?: continue
            for (k in kids) if (k.name != "emulated" && k.name != "self") add(k)
        }
        add(File("/storage/emulated/0/Music"))
        return found.values.toList()
    }

    fun scan(roots: List<File>): List<Track> {
        val out = ArrayList<Track>()
        val stack = ArrayList<Pair<File, Int>>()
        for (r in roots) stack.add(r to 0)

        while (stack.isNotEmpty() && out.size < MAX_FILES) {
            val (dir, depth) = stack.removeAt(stack.size - 1)
            val kids = try {
                dir.listFiles()
            } catch (e: Exception) {
                null
            } ?: continue
            for (f in kids) {
                val name = f.name
                if (name.startsWith(".")) continue
                if (f.isDirectory) {
                    if (depth < MAX_DEPTH && name.lowercase() !in SKIP_DIRS) stack.add(f to depth + 1)
                } else if (name.substringAfterLast('.', "").lowercase() in EXTENSIONS) {
                    out.add(Track(f.absolutePath, name.substringBeforeLast('.'), dir.absolutePath, dir.name))
                    if (out.size >= MAX_FILES) break
                }
            }
        }
        return out.sortedWith(compareBy({ it.folderName.lowercase() }, { it.title.lowercase() }))
    }

    /** Lista de carpetas; la primera es "Todas las canciones". */
    fun folders(tracks: List<Track>): List<Folder> {
        if (tracks.isEmpty()) return emptyList()
        val byFolder = tracks.groupBy { it.folderPath }
            .map { (path, list) -> Folder(path, list.first().folderName, list.size) }
            .sortedBy { it.name.lowercase() }
        return listOf(Folder(ALL, "Todas las canciones", tracks.size)) + byFolder
    }

    fun songsIn(tracks: List<Track>, folderPath: String?): List<Track> = when (folderPath) {
        null -> emptyList()
        ALL -> tracks
        else -> tracks.filter { it.folderPath == folderPath }
    }

    fun folderLabel(folderPath: String): String =
        if (folderPath == ALL) "Todas las canciones" else File(folderPath).name
}

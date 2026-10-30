package com.mivehiculo.launcher.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Estado del mantenimiento. Los km restantes se calculan siempre a partir de estos 4 datos. */
data class MaintenanceData(
    val currentKm: Int = 0,
    val intervalKm: Int = 5000,
    val lastKm: Int? = null,
    val lastDateMs: Long? = null,
) {
    /** Km recorridos desde el último cambio: actual - último. */
    val usedKm: Int? get() = lastKm?.let { currentKm - it }

    /** Km que faltan: intervalo - recorridos. Negativo = ya se pasó. */
    val remainingKm: Int? get() = usedKm?.let { intervalKm - it }

    /** Km del odómetro en el que toca el próximo cambio. */
    val nextKm: Int? get() = lastKm?.let { it + intervalKm }
}

object KmFormat {
    /** 4990 -> "4.990" */
    fun km(n: Int): String {
        val digits = abs(n).toString().reversed().chunked(3).joinToString(".").reversed()
        return if (n < 0) "-$digits" else digits
    }

    fun date(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ms))
}

/**
 * Base de datos SQLite en el almacenamiento interno de la app (archivo "maintenance.db").
 * Dos tablas: "vehicle" (km actual e intervalo) y "oil_changes" (historial de cambios de aceite).
 * Todos los métodos son síncronos: llamarlos SIEMPRE fuera del hilo principal.
 */
class MaintenanceRepo private constructor(context: Context) :
    SQLiteOpenHelper(context, "maintenance.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE vehicle (" +
                "id INTEGER PRIMARY KEY, " +
                "current_km INTEGER NOT NULL, " +
                "interval_km INTEGER NOT NULL)"
        )
        db.execSQL("INSERT INTO vehicle (id, current_km, interval_km) VALUES (1, 0, 5000)")
        db.execSQL(
            "CREATE TABLE oil_changes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "date_ms INTEGER NOT NULL, " +
                "km INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versión 1: nada que migrar todavía.
    }

    fun load(): MaintenanceData {
        val db = readableDatabase
        var current = 0
        var interval = 5000
        db.rawQuery("SELECT current_km, interval_km FROM vehicle WHERE id = 1", null).use { c ->
            if (c.moveToFirst()) {
                current = c.getInt(0)
                interval = c.getInt(1)
            }
        }
        var lastKm: Int? = null
        var lastDate: Long? = null
        db.rawQuery("SELECT date_ms, km FROM oil_changes ORDER BY id DESC LIMIT 1", null).use { c ->
            if (c.moveToFirst()) {
                lastDate = c.getLong(0).takeIf { it > 0L } // 0 = fecha no registrada
                lastKm = c.getInt(1)
            }
        }
        return MaintenanceData(current, interval, lastKm, lastDate)
    }

    fun setCurrentKm(km: Int) {
        writableDatabase.execSQL("UPDATE vehicle SET current_km = ? WHERE id = 1", arrayOf<Any>(km))
    }

    fun setInterval(km: Int) {
        writableDatabase.execSQL("UPDATE vehicle SET interval_km = ? WHERE id = 1", arrayOf<Any>(km))
    }

    /** "Hice el cambio hoy": agrega una fila nueva al historial. */
    fun registerChange(dateMs: Long, km: Int) {
        writableDatabase.execSQL(
            "INSERT INTO oil_changes (date_ms, km) VALUES (?, ?)",
            arrayOf<Any>(dateMs, km),
        )
    }

    /** Corrige el km del último cambio; si no hay ninguno, lo crea (con fecha "no registrada"). */
    fun setLastChangeKm(km: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE oil_changes SET km = ? WHERE id = (SELECT MAX(id) FROM oil_changes)",
                arrayOf<Any>(km),
            )
            db.execSQL(
                "INSERT INTO oil_changes (date_ms, km) SELECT 0, ? WHERE NOT EXISTS (SELECT 1 FROM oil_changes)",
                arrayOf<Any>(km),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        @Volatile
        private var instance: MaintenanceRepo? = null

        fun get(context: Context): MaintenanceRepo =
            instance ?: synchronized(this) {
                instance ?: MaintenanceRepo(context.applicationContext).also { instance = it }
            }
    }
}

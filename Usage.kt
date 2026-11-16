package com.plan.ingsoft

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

data class AppUso(val pkg: String, val label: String, val ms: Long)

data class UsoResumen(
    val totalHoyMs: Long,
    val apps: List<AppUso>,
    val semana: List<Pair<LocalDate, Long>>
)

/** Lectura del tiempo en pantalla con UsageStatsManager (requiere "Acceso al uso" en Ajustes). */
object Usage {

    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun startOfDay(d: LocalDate): Long =
        d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Milisegundos en primer plano por paquete entre [from] y [to].
     * Se mira un poco antes de [from] para no perder la app que ya estaba abierta (p. ej. a medianoche)
     * y los tramos se recortan a [from]..[to].
     */
    fun porApp(ctx: Context, from: Long, to: Long): Map<String, Long> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(from - MIRAR_ATRAS_MS, to) ?: return emptyMap()
        val e = UsageEvents.Event()
        val resumed = HashMap<String, Long>()
        val out = HashMap<String, Long>()
        fun sumar(p: String, ini: Long, fin: Long) {
            val a = maxOf(ini, from)
            val b = minOf(fin, to)
            if (b > a) out[p] = (out[p] ?: 0L) + (b - a)
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val p = e.packageName ?: continue
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                resumed[p] = e.timeStamp
            } else if (e.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                val s = resumed.remove(p)
                if (s != null) sumar(p, s, e.timeStamp)
            }
        }
        for ((p, s) in resumed) sumar(p, s, to)
        return out
    }

    private const val MIRAR_ATRAS_MS = 12 * 60 * 60 * 1000L

    fun hoyMs(ctx: Context, pkg: String): Long {
        val now = System.currentTimeMillis()
        return porApp(ctx, startOfDay(LocalDate.now()), now)[pkg] ?: 0L
    }

    /** App en primer plano tras procesar los eventos desde [from]; [actual] es la anterior. */
    fun primerPlano(ctx: Context, from: Long, to: Long, actual: String?): String? {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(from, to) ?: return actual
        val e = UsageEvents.Event()
        var cur = actual
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                cur = e.packageName
            } else if (e.eventType == UsageEvents.Event.ACTIVITY_PAUSED && e.packageName == cur) {
                cur = null
            }
        }
        return cur
    }

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        Log.w("Usage", "Sin etiqueta para $pkg", e)
        pkg
    }

    private fun homePkgs(ctx: Context): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
    }

    /** Solo apps "reales": con icono de lanzador, sin esta app ni el launcher. */
    fun visibles(ctx: Context, m: Map<String, Long>): List<AppUso> {
        val pm = ctx.packageManager
        val home = homePkgs(ctx)
        return m.entries
            .filter { it.value >= 1000 && it.key != ctx.packageName && it.key !in home }
            .filter { pm.getLaunchIntentForPackage(it.key) != null }
            .map { AppUso(it.key, label(ctx, it.key), it.value) }
            .sortedByDescending { it.ms }
    }

    /** Días ya terminados no cambian: se calculan una vez y se recuerdan mientras la app viva. */
    private val diasPasados = HashMap<LocalDate, Long>()

    /** Resumen para la pantalla Foco (llamar fuera del hilo principal). */
    fun resumen(ctx: Context): UsoResumen {
        val hoy = LocalDate.now()
        val now = System.currentTimeMillis()
        val apps = visibles(ctx, porApp(ctx, startOfDay(hoy), now))
        val semana = ArrayList<Pair<LocalDate, Long>>()
        for (k in 6 downTo 0) {
            val d = hoy.minusDays(k.toLong())
            if (k == 0) {
                semana.add(d to apps.sumOf { it.ms })
            } else {
                val v = synchronized(diasPasados) { diasPasados[d] } ?: run {
                    val calc = visibles(ctx, porApp(ctx, startOfDay(d), startOfDay(d.plusDays(1)))).sumOf { it.ms }
                    synchronized(diasPasados) {
                        diasPasados[d] = calc
                        // Solo se guardan los últimos días.
                        diasPasados.keys.removeAll { it.isBefore(hoy.minusDays(8)) }
                    }
                    calc
                }
                semana.add(d to v)
            }
        }
        return UsoResumen(apps.sumOf { it.ms }, apps, semana)
    }
}

fun fmtMs(ms: Long): String {
    val min = ms / 60_000
    if (min < 1) return "<1 min"
    val h = min / 60
    val m = min % 60
    return if (h > 0) "$h h ${"%02d".format(m)} min" else "$m min"
}

package com.plan.ingsoft

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Lee tu horario desde una hoja de Google Sheets compartida por enlace («Cualquier persona con el enlace»).
 * La pestaña «Horario» manda: lo que cambies allí reemplaza el horario de la app.
 * Pestaña opcional «Plan» (Clave | Valor) con «inicio» y «eficiencia».
 * Todo lo de esta parte es lógica pura (sin Android) para poder probarla con pruebas unitarias.
 */
object HorarioSheet {
    private const val TAG = "HorarioSheet"
    private const val TRABAJO = "horario_hoja"
    private const val MIN_ENTRE_LECTURAS_MS = 10 * 60_000L

    class Resultado(val ok: Boolean, val mensaje: String, val cambio: Boolean = false, val reintentar: Boolean = false)

    class Lectura(val bloques: List<Block>, val ignoradas: Int)

    class AjustesHoja(val inicio: LocalDate?, val eficiencia: Double?)

    // ------------------------------------------------------------ enlaces

    /** Saca el id de un enlace de Google Sheets (también acepta el id suelto). */
    fun idDe(texto: String): String? {
        val t = texto.trim()
        val m = Regex("/spreadsheets/d/([A-Za-z0-9_-]{20,})").find(t)
        if (m != null) return m.groupValues[1]
        return if (Regex("^[A-Za-z0-9_-]{20,}$").matches(t)) t else null
    }

    fun urlAbrir(id: String): String = "https://docs.google.com/spreadsheets/d/$id/edit"

    fun urlCsv(id: String, hoja: String): String =
        "https://docs.google.com/spreadsheets/d/$id/gviz/tq?tqx=out:csv&headers=1&sheet=" +
            URLEncoder.encode(hoja, "UTF-8")

    // ------------------------------------------------------------ lectura de celdas

    /** CSV con comillas (RFC 4180): campos entre comillas, comillas dobles y saltos de línea dentro. */
    fun parseCsv(txt: String): List<List<String>> {
        val t = txt.removePrefix("﻿")
        val filas = ArrayList<List<String>>()
        var fila = ArrayList<String>()
        val sb = StringBuilder()
        var entre = false
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (entre) {
                if (c == '"') {
                    if (i + 1 < t.length && t[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        entre = false
                    }
                } else {
                    sb.append(c)
                }
            } else if (c == '"') {
                entre = true
            } else if (c == ',') {
                fila.add(sb.toString())
                sb.setLength(0)
            } else if (c == '\n') {
                fila.add(sb.toString())
                sb.setLength(0)
                filas.add(fila)
                fila = ArrayList()
            } else if (c != '\r') {
                sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || fila.isNotEmpty()) {
            fila.add(sb.toString())
            filas.add(fila)
        }
        return filas
    }

    /** Minúsculas y sin tildes: "Miércoles " -> "miercoles". */
    fun norm(s: String): String =
        Normalizer.normalize(s.replace(' ', ' ').trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    fun parseDia(s: String): String? = when (norm(s).take(3)) {
        "lun" -> "Lunes"
        "mar" -> "Martes"
        "mie" -> "Miércoles"
        "jue" -> "Jueves"
        "vie" -> "Viernes"
        "sab" -> "Sábado"
        "dom" -> "Domingo"
        else -> null
    }

    /** Acepta 19:00, 7:00 PM, 7:00:00 p. m., 19:00:00 y también el número de día de Sheets (0,7916 = 19:00). */
    fun parseHora(s: String): LocalTime? {
        var t = norm(s).replace(".", "").replace(" ", "")
        val pm = t.endsWith("pm")
        val am = t.endsWith("am")
        if (pm || am) t = t.dropLast(2)
        val m = Regex("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$").matchEntire(t)
        if (m != null) {
            var h = m.groupValues[1].toInt()
            val mi = m.groupValues[2].toInt()
            if (mi > 59) return null
            if (h == 24 && mi == 0 && !pm && !am) return LocalTime.MIDNIGHT
            if (pm && h in 1..11) h += 12
            if (am && h == 12) h = 0
            if (h > 23) return null
            return LocalTime.of(h, mi)
        }
        val d = t.replace(',', '.').toDoubleOrNull()
        if (d != null && d >= 0.0 && d <= 1.0) {
            val total = Math.round(d * 1440).toInt() % 1440
            return LocalTime.of(total / 60, total % 60)
        }
        return null
    }

    fun parsePilar(s: String): String {
        val n = norm(s)
        return when {
            n.startsWith("estud") -> "Estudio"
            n.startsWith("entren") || n.startsWith("ejerc") || n.startsWith("workout") || n.startsWith("gym") -> "Entrenamiento"
            n.startsWith("relac") || n.startsWith("pareja") -> "Relación"
            else -> "Personal"
        }
    }

    private fun siNo(s: String, defecto: Boolean): Boolean = when (norm(s)) {
        "" -> defecto
        "no", "false", "0", "n", "apagado", "off" -> false
        else -> true
    }

    /** Día/mes/año (Latinoamérica) o año-mes-día. */
    fun parseFecha(s: String): LocalDate? {
        val t = s.trim()
        return try {
            val iso = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").matchEntire(t)
            val lat = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$").matchEntire(t)
            when {
                iso != null -> LocalDate.of(iso.groupValues[1].toInt(), iso.groupValues[2].toInt(), iso.groupValues[3].toInt())
                lat != null -> LocalDate.of(lat.groupValues[3].toInt(), lat.groupValues[2].toInt(), lat.groupValues[1].toInt())
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** "0,85", "0.85" o "85%" -> 0.85 (solo entre 0,30 y 1,00). */
    fun parseEficiencia(s: String): Double? {
        val t = s.trim().replace(',', '.')
        val v = if (t.endsWith("%")) t.dropLast(1).trim().toDoubleOrNull()?.div(100) else t.toDoubleOrNull()
        return if (v != null && v >= 0.3 && v <= 1.0) v else null
    }

    // ------------------------------------------------------------ de filas a bloques

    private fun columna(k: String): String? = when (k) {
        "dia" -> "dia"
        "inicio", "desde", "hora inicio" -> "ini"
        "fin", "hasta", "hora fin" -> "fin"
        "pilar", "tipo", "categoria" -> "pilar"
        "actividad", "tarea", "descripcion", "detalle" -> "act"
        "lugar", "donde", "ubicacion" -> "lugar"
        "notificar", "aviso", "avisar" -> "notif"
        "pista" -> "pista"
        else -> null
    }

    /** Devuelve null si no encuentra las columnas Día, Inicio, Fin y Actividad. */
    fun bloquesDeFilas(filas: List<List<String>>): Lectura? {
        var cab = -1
        var cols: Map<String, Int> = emptyMap()
        for (r in 0 until minOf(3, filas.size)) {
            val m = HashMap<String, Int>()
            filas[r].forEachIndexed { c, v ->
                val k = columna(norm(v))
                if (k != null && !m.containsKey(k)) m[k] = c
            }
            if (m.containsKey("dia") && m.containsKey("ini") && m.containsKey("fin") && m.containsKey("act")) {
                cab = r
                cols = m
                break
            }
        }
        if (cab < 0) return null

        val vistos = HashMap<String, Int>()
        val ids = HashSet<Int>()
        val out = ArrayList<Block>()
        var ignoradas = 0
        for (r in cab + 1 until filas.size) {
            val f = filas[r]
            if (f.all { it.isBlank() }) continue
            fun cel(k: String): String {
                val c = cols[k] ?: return ""
                return (f.getOrNull(c) ?: "").trim()
            }
            val dia = parseDia(cel("dia"))
            val ini = parseHora(cel("ini"))
            val fin = parseHora(cel("fin"))
            val act = cel("act")
            if (dia == null || ini == null || fin == null || act.isEmpty() || ini == fin) {
                ignoradas++
                continue
            }
            val pilar = parsePilar(cel("pilar"))
            val pistaTxt = cel("pista").uppercase()
            val pista = when {
                pilar != "Estudio" -> ""
                pistaTxt == "B" || (pistaTxt.isEmpty() && act.startsWith("Segundo curso")) -> "B"
                else -> "A"
            }
            // El id depende del día y la actividad (no de la hora): si cambias la hora en la hoja,
            // las marcas de "hecho" de ese bloque se conservan.
            val clave = "$dia|$act"
            val n = vistos[clave] ?: 0
            vistos[clave] = n + 1
            var id = 1_000_000 + (clave.hashCode() and 0xFFFFF) * 8 + minOf(n, 7)
            while (!ids.add(id)) id++
            out.add(Block(id, dia, ini, fin, pilar, act, cel("lugar"), siNo(cel("notif"), true), pista))
        }
        return Lectura(out, ignoradas)
    }

    fun ajustesDeFilas(filas: List<List<String>>): AjustesHoja {
        var ini: LocalDate? = null
        var efi: Double? = null
        for (f in filas) {
            if (f.size < 2) continue
            when (norm(f[0])) {
                "inicio", "fecha de inicio", "fecha inicio" -> ini = parseFecha(f[1]) ?: ini
                "eficiencia" -> efi = parseEficiencia(f[1]) ?: efi
            }
        }
        return AjustesHoja(ini, efi)
    }

    // ------------------------------------------------------------ red

    private fun leer(id: String, hoja: String): String {
        val c = URL(urlCsv(id, hoja)).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            val code = c.responseCode
            if (code == 400 || code == 404) throw IOException("No encuentro la hoja o la pestaña «$hoja» (HTTP $code)")
            if (code == 401 || code == 403) throw IOException("La hoja es privada. Compártela con «Cualquier persona con el enlace»")
            if (code !in 200..299) throw IOException("HTTP $code")
            val txt = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (txt.trimStart().startsWith("<")) {
                throw IOException("La hoja es privada. Compártela con «Cualquier persona con el enlace» (lector)")
            }
            return txt
        } finally {
            c.disconnect()
        }
    }

    private fun ahora(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM HH:mm", LOCALE_ES))

    private fun guardar(st: Store, r: Resultado): Resultado {
        st.sheetsHorarioUltimo = r.mensaje
        return r
    }

    /** Lee la hoja y reemplaza el horario de la app. Llamar fuera del hilo principal. */
    fun traer(ctx: Context): Resultado {
        val app = ctx.applicationContext
        val st = Store(app)
        val id = st.sheetsHorarioId
        if (id.isBlank()) return Resultado(false, "Falta el enlace de la hoja")
        st.sheetsHorarioTs = System.currentTimeMillis()
        return try {
            val lectura = bloquesDeFilas(parseCsv(leer(id, "Horario")))
                ?: return guardar(
                    st,
                    Resultado(false, "No encuentro las columnas Día, Inicio, Fin y Actividad en la pestaña «Horario»")
                )
            if (lectura.bloques.isEmpty()) {
                return guardar(st, Resultado(false, "La pestaña «Horario» no tiene filas válidas. No cambié nada"))
            }
            val aj = try {
                ajustesDeFilas(parseCsv(leer(id, "Plan")))
            } catch (e: Exception) {
                AjustesHoja(null, null) // la pestaña «Plan» es opcional
            }

            val nuevo = blocksToJson(lectura.bloques)
            var cambio = nuevo != st.horarioJson()
            if (cambio) st.saveHorarioJson(nuevo)
            val ini = aj.inicio
            if (ini != null && ini.toString() != st.inicioOverride()) {
                st.setInicioOverride(ini.toString())
                cambio = true
            }
            val efi = aj.eficiencia
            val efiActual = st.eficienciaOverride()
            if (efi != null && (efiActual == null || Math.abs(efiActual - efi) > 0.0001)) {
                st.setEficienciaOverride(efi)
                cambio = true
            }

            val msg = "Horario leído · ${lectura.bloques.size} bloques" +
                (if (lectura.ignoradas > 0) " · ${lectura.ignoradas} filas ignoradas" else "") +
                " · ${ahora()}"
            if (cambio) {
                PlanLoader.reload(app)
                Notifier.scheduleNext(app)
                PlanWidget.updateAll(app)
            }
            guardar(st, Resultado(true, msg, cambio))
        } catch (e: IOException) {
            Log.w(TAG, "No se pudo leer la hoja", e)
            guardar(st, Resultado(false, e.message ?: "Sin conexión", reintentar = true))
        } catch (e: Exception) {
            Log.w(TAG, "Fallo inesperado al leer la hoja", e)
            guardar(st, Resultado(false, "Error: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    /** Al abrir la app: lee la hoja si pasaron más de 10 minutos desde el último intento. */
    fun traerSiToca(ctx: Context): Resultado? {
        val st = Store(ctx)
        if (!st.sheetsHorarioAuto || st.sheetsHorarioId.isBlank()) return null
        if (System.currentTimeMillis() - st.sheetsHorarioTs < MIN_ENTRE_LECTURAS_MS) return null
        return traer(ctx)
    }

    /** Activa o desactiva la lectura automática en segundo plano (cada 3 h con internet). */
    fun programar(ctx: Context) {
        val app = ctx.applicationContext
        val st = Store(app)
        val wm = WorkManager.getInstance(app)
        if (st.sheetsHorarioAuto && st.sheetsHorarioId.isNotBlank()) {
            val req = PeriodicWorkRequestBuilder<HorarioWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(TRABAJO, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(TRABAJO)
        }
    }
}

/** Trabajo en segundo plano: lee tu horario de la hoja de Google cuando hay internet. */
class HorarioWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        val r = HorarioSheet.traer(applicationContext)
        when {
            r.ok -> ListenableWorker.Result.success()
            r.reintentar && runAttemptCount < 3 -> ListenableWorker.Result.retry()
            else -> ListenableWorker.Result.failure()
        }
    }
}

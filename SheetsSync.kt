package com.plan.ingsoft

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Copia de seguridad en un archivo (JSON) que contiene tu progreso y ajustes. */
object Respaldo {
    fun exportarTexto(st: Store): String =
        JSONObject()
            .put("app", "plan-ing-software")
            .put("version", 1)
            .put("datos", st.exportar())
            .toString()

    /** Lanza una excepción si el texto no es una copia válida. */
    fun importarTexto(st: Store, txt: String) {
        val o = JSONObject(txt)
        if (o.optString("app") != "plan-ing-software") {
            throw IllegalArgumentException("El archivo no es una copia de esta app")
        }
        val datos = o.optJSONObject("datos") ?: throw IllegalArgumentException("La copia está vacía")
        st.importar(datos)
    }
}

/**
 * Sincroniza tu progreso con una hoja de Google Sheets mediante un pequeño Apps Script
 * (ver google-sheets/Codigo.gs). La app envía una foto completa; la hoja es una vista y un respaldo.
 */
object SheetsSync {
    private const val TAG = "SheetsSync"
    private const val TRABAJO_PERIODICO = "sheets_periodico"
    private const val TRABAJO_PRONTO = "sheets_pronto"
    private const val DIAS_DE_BLOQUES = 60L

    class Resultado(val ok: Boolean, val mensaje: String, val reintentar: Boolean = false)

    fun configurado(st: Store): Boolean =
        st.sheetsUrl.startsWith("https://script.google.com/") && st.sheetsToken.isNotBlank()

    private fun conRed(): Constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Activa o desactiva la sincronización automática (cada 6 h con internet). */
    fun programar(ctx: Context) {
        val app = ctx.applicationContext
        val st = Store(app)
        val wm = WorkManager.getInstance(app)
        if (st.sheetsAuto && configurado(st)) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(conRed()).build()
            wm.enqueueUniquePeriodicWork(TRABAJO_PERIODICO, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(TRABAJO_PERIODICO)
            wm.cancelUniqueWork(TRABAJO_PRONTO)
        }
    }

    /** Tras un cambio de progreso: envía en ~45 s (si hay varios cambios seguidos, espera al último). */
    fun pedirSincronizacion(ctx: Context) {
        val app = ctx.applicationContext
        val st = Store(app)
        if (!st.sheetsAuto || !configurado(st)) return
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(conRed())
            .setInitialDelay(45, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(TRABAJO_PRONTO, ExistingWorkPolicy.REPLACE, req)
    }

    // ------------------------------------------------------------ construir tablas

    private fun fila(vararg v: Any?): JSONArray {
        val a = JSONArray()
        for (x in v) a.put(x ?: "")
        return a
    }

    private fun tabla(cabecera: List<String>, filas: List<JSONArray>): JSONArray {
        val t = JSONArray()
        t.put(JSONArray(cabecera))
        for (f in filas) t.put(f)
        return t
    }

    private fun r1(x: Double): Double = Math.round(x * 10.0) / 10.0

    private fun ahora(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM HH:mm", LOCALE_ES))

    private fun tablas(ctx: Context, st: Store): JSONObject {
        val plan = PlanLoader.get(ctx)
        val hoy = LocalDate.now()
        val out = JSONObject()

        val total = plan.horasTotal()
        val hechas = plan.horasHechas(st)
        out.put(
            "Resumen",
            tabla(
                listOf("Dato", "Valor"),
                listOf(
                    fila("Actualizado", LocalDateTime.now().toString()),
                    fila("Inicio del plan", plan.inicio.toString()),
                    fila("Semana actual", plan.semanaActual(hoy)),
                    fila("Plan terminado", plan.terminado(hoy)),
                    fila("Horas hechas", r1(hechas)),
                    fila("Horas totales", r1(total)),
                    fila("Avance (%)", if (total > 0) r1(hechas / total * 100) else 0.0),
                    fila("Horas esperadas a hoy", r1(plan.horasEsperadas(hoy))),
                    fila("Hitos hechos", plan.hitosHechos(st)),
                    fila("Hitos totales", plan.hitos.size),
                    fila("Estado", plan.estadoGlobal(st, hoy)),
                    fila("Racha (días)", plan.racha(st, hoy))
                )
            )
        )

        out.put(
            "Hitos",
            tabla(
                listOf("ID", "Curso", "Hito", "Horas", "Hecho", "Fecha"),
                plan.hitos.map { h ->
                    val hecho = st.isHitoDone(h.id)
                    fila(
                        h.id, h.curso, h.texto, h.horas, hecho,
                        if (hecho) LocalDate.ofEpochDay(st.hitoFecha(h.id)).toString() else ""
                    )
                }
            )
        )

        val filasBloques = ArrayList<JSONArray>()
        val limiteAtras = hoy.minusDays(DIAS_DE_BLOQUES)
        var d: LocalDate = if (plan.inicio.isAfter(limiteAtras)) plan.inicio else limiteAtras
        while (!d.isAfter(hoy)) {
            for (b in plan.bloquesDe(d)) {
                if (!b.checkable()) continue
                filasBloques.add(
                    fila(
                        d.toString(), diaNombre(d), b.ini.toString(), b.fin.toString(),
                        b.pilar, b.act, b.lugar, st.isBlockDone(d, b.id)
                    )
                )
            }
            d = d.plusDays(1)
        }
        out.put(
            "Bloques",
            tabla(listOf("Fecha", "Día", "Inicio", "Fin", "Pilar", "Actividad", "Lugar", "Hecho"), filasBloques)
        )

        val nombresSesion = listOf("Lun (A)", "Mié (B)", "Vie (A)", "Sáb caminata", "Dom movilidad")
        val filasEntreno = ArrayList<JSONArray>()
        for (w in 1..SEMANAS_PLAN) {
            for (i in 0..4) filasEntreno.add(fila(w, nombresSesion[i], st.isTrainDone(w, i)))
        }
        out.put("Entreno", tabla(listOf("Semana", "Sesión", "Hecho"), filasEntreno))

        out.put(
            "Tests",
            tabla(
                listOf("Prueba") + TEST_COLS,
                TESTS.mapIndexed { p, nombre ->
                    fila(nombre, st.testVal(p, 0), st.testVal(p, 1), st.testVal(p, 2), st.testVal(p, 3))
                }
            )
        )

        if (Usage.hasAccess(ctx)) {
            try {
                val semana = Usage.resumen(ctx).semana
                out.put(
                    "Foco",
                    tabla(
                        listOf("Fecha", "Minutos en pantalla"),
                        semana.map { (f, ms) -> fila(f.toString(), ms / 60_000) }
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo leer el tiempo en pantalla para la hoja", e)
            }
        }
        return out
    }

    private fun payloadEnviar(ctx: Context, st: Store): String =
        JSONObject()
            .put("token", st.sheetsToken)
            .put("accion", "enviar")
            .put("dispositivo", Build.MODEL ?: "")
            .put("tablas", tablas(ctx, st))
            .put("respaldo", Respaldo.exportarTexto(st))
            .toString()

    // ------------------------------------------------------------ red

    private fun post(url: String, cuerpo: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.doOutput = true
            c.instanceFollowRedirects = true // Apps Script responde con una redirección
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val flujo = if (code in 200..299) c.inputStream else c.errorStream
            val txt = flujo?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code")
            return txt
        } finally {
            c.disconnect()
        }
    }

    private fun interpretar(txt: String): JSONObject = try {
        JSONObject(txt)
    } catch (e: JSONException) {
        throw IOException(
            "La respuesta no es la esperada. Revisa que la implementación del script " +
                "esté como «Cualquier persona» y que la URL termine en /exec."
        )
    }

    /** Envía la foto actual a la hoja. Llamar fuera del hilo principal. */
    fun enviar(ctx: Context): Resultado {
        val app = ctx.applicationContext
        val st = Store(app)
        if (!configurado(st)) return Resultado(false, "Falta la URL del script o el token")
        return try {
            val o = interpretar(post(st.sheetsUrl, payloadEnviar(app, st)))
            if (o.optBoolean("ok")) {
                val msg = "Enviado · ${ahora()}"
                st.sheetsUltimo = msg
                Resultado(true, msg)
            } else {
                val msg = "La hoja respondió: ${o.optString("error", "error desconocido")}"
                st.sheetsUltimo = msg
                Resultado(false, msg)
            }
        } catch (e: IOException) {
            Log.w(TAG, "No se pudo enviar a Google Sheets", e)
            val msg = "Sin conexión o error de red: ${e.message ?: ""}".trim()
            st.sheetsUltimo = msg
            Resultado(false, msg, reintentar = true)
        } catch (e: Exception) {
            Log.w(TAG, "Fallo inesperado al enviar a Google Sheets", e)
            val msg = "Error: ${e.message ?: e.javaClass.simpleName}"
            st.sheetsUltimo = msg
            Resultado(false, msg)
        }
    }

    /** Trae el último respaldo guardado en la hoja y reemplaza tu progreso local. */
    fun restaurar(ctx: Context): Resultado {
        val app = ctx.applicationContext
        val st = Store(app)
        if (!configurado(st)) return Resultado(false, "Falta la URL del script o el token")
        return try {
            val cuerpo = JSONObject().put("token", st.sheetsToken).put("accion", "restaurar").toString()
            val o = interpretar(post(st.sheetsUrl, cuerpo))
            if (!o.optBoolean("ok")) {
                return Resultado(false, "La hoja respondió: ${o.optString("error", "error desconocido")}")
            }
            val txt = o.optString("respaldo")
            if (txt.isBlank()) return Resultado(false, "La hoja todavía no tiene un respaldo")
            Respaldo.importarTexto(st, txt)
            PlanLoader.reload(app)
            Notifier.scheduleNext(app)
            PlanWidget.updateAll(app)
            Resultado(true, "Progreso restaurado desde la hoja")
        } catch (e: IOException) {
            Log.w(TAG, "No se pudo leer el respaldo de Google Sheets", e)
            Resultado(false, "Sin conexión o error de red: ${e.message ?: ""}".trim())
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al restaurar desde Google Sheets", e)
            Resultado(false, "Error: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}

/** Trabajo en segundo plano: manda el progreso a la hoja cuando hay internet. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        val r = SheetsSync.enviar(applicationContext)
        when {
            r.ok -> ListenableWorker.Result.success()
            r.reintentar && runAttemptCount < 3 -> ListenableWorker.Result.retry()
            else -> ListenableWorker.Result.failure()
        }
    }
}

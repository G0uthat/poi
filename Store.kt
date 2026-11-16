package com.plan.ingsoft

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** Lo mínimo que necesita la lógica del plan para saber qué has hecho (permite probarla sin Android). */
interface Progreso {
    fun isHitoDone(id: String): Boolean
    fun hitoFecha(id: String): Long
    fun isBlockDone(date: LocalDate, id: Int): Boolean
}

/** Todo el progreso y los ajustes viven en el teléfono (SharedPreferences). */
class Store(ctx: Context) : Progreso {
    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("plan", Context.MODE_PRIVATE)

    // ---- Hitos ----
    override fun isHitoDone(id: String): Boolean = sp.contains("h_$id")
    override fun hitoFecha(id: String): Long = sp.getLong("h_$id", 0L)
    fun setHito(id: String, done: Boolean) {
        val e = sp.edit()
        if (done) e.putLong("h_$id", LocalDate.now().toEpochDay()) else e.remove("h_$id")
        e.apply()
    }

    // ---- Bloques del horario (por fecha) ----
    // Solo se guardan los marcados: al desmarcar se borra la clave (así el archivo no crece de más).
    override fun isBlockDone(date: LocalDate, id: Int): Boolean = sp.getBoolean("b_${date}_$id", false)
    fun setBlock(date: LocalDate, id: Int, done: Boolean) {
        val e = sp.edit()
        if (done) e.putBoolean("b_${date}_$id", true) else e.remove("b_${date}_$id")
        e.apply()
    }

    /**
     * Id nuevo para un bloque. Nunca se reutiliza el de un bloque borrado, para que un bloque
     * nuevo no herede las marcas históricas de otro.
     */
    fun reservarIdBloque(maxActual: Int): Int {
        val siguiente = maxOf(maxActual + 1, sp.getInt("next_block_id", 0))
        sp.edit().putInt("next_block_id", siguiente + 1).apply()
        return siguiente
    }

    // ---- Entrenamiento (semana 1..12, sesión 0..4) ----
    fun isTrainDone(week: Int, i: Int): Boolean = sp.getBoolean("t_${week}_$i", false)
    fun setTrain(week: Int, i: Int, done: Boolean) {
        val e = sp.edit()
        if (done) e.putBoolean("t_${week}_$i", true) else e.remove("t_${week}_$i")
        e.apply()
    }

    // ---- Test de rendimiento (prueba 0..4, columna 0..3) ----
    fun testVal(p: Int, c: Int): String = sp.getString("test_${p}_$c", "") ?: ""
    fun setTestVal(p: Int, c: Int, v: String) {
        val e = sp.edit()
        if (v.isEmpty()) e.remove("test_${p}_$c") else e.putString("test_${p}_$c", v)
        e.apply()
    }

    // ---- Horario y plan editables ----
    fun horarioJson(): String? = sp.getString("horario_json", null)
    fun saveHorarioJson(s: String?) {
        val e = sp.edit()
        if (s == null) e.remove("horario_json") else e.putString("horario_json", s)
        e.remove("horario_danado")
        e.apply()
    }

    /** Si el horario guardado no se pudo leer, se conserva una copia y se avisa en Ajustes. */
    fun marcarHorarioDanado(copia: String) {
        sp.edit().putString("horario_danado", copia).apply()
    }

    fun horarioDanado(): Boolean = sp.contains("horario_danado")
    fun copiaHorarioDanado(): String? = sp.getString("horario_danado", null)

    fun inicioOverride(): String? = sp.getString("inicio_ov", null)
    fun setInicioOverride(s: String?) {
        val e = sp.edit()
        if (s == null) e.remove("inicio_ov") else e.putString("inicio_ov", s)
        e.apply()
    }

    fun eficienciaOverride(): Double? =
        if (sp.contains("efic_ov")) sp.getFloat("efic_ov", 0.85f).toDouble() else null

    fun setEficienciaOverride(v: Double?) {
        val e = sp.edit()
        if (v == null) e.remove("efic_ov") else e.putFloat("efic_ov", v.toFloat())
        e.apply()
    }

    // ---- Notificaciones ----
    var notifOn: Boolean
        get() = sp.getBoolean("notif_on", true)
        set(v) { sp.edit().putBoolean("notif_on", v).apply() }

    var resumen: Boolean
        get() = sp.getBoolean("resumen", true)
        set(v) { sp.edit().putBoolean("resumen", v).apply() }

    var resumenHora: String
        get() = sp.getString("resumen_hora", "08:00") ?: "08:00"
        set(v) { sp.edit().putString("resumen_hora", v).apply() }

    /** Minutos de pre-aviso antes de Estudio/Entreno (0 = sin pre-aviso). */
    var preMin: Int
        get() = sp.getInt("pre_min", 10)
        set(v) { sp.edit().putInt("pre_min", v).apply() }

    /** Pregunta "¿Terminaste?" al acabar un bloque de Estudio/Entreno. */
    var askEnd: Boolean
        get() = sp.getBoolean("ask_end", true)
        set(v) { sp.edit().putBoolean("ask_end", v).apply() }

    fun notifPilar(p: String): Boolean = sp.getBoolean("n_$p", p != "Personal")
    fun setNotifPilar(p: String, v: Boolean) {
        sp.edit().putBoolean("n_$p", v).apply()
    }

    /** Momento (ms) del último aviso ya disparado; evita perder o repetir avisos. */
    var ultimoAvisoMs: Long
        get() = sp.getLong("ultimo_aviso_ms", 0L)
        set(v) { sp.edit().putLong("ultimo_aviso_ms", v).apply() }

    // ---- Contraseña de edición ----
    // pw_v = 2 -> PBKDF2. Sin pw_v -> formato antiguo (SHA-256), se migra al primer acierto.
    fun pwSalt(): String? = sp.getString("pw_salt", null)
    fun pwHash(): String? = sp.getString("pw_hash", null)
    fun pwVersion(): Int = sp.getInt("pw_v", 1)
    fun savePassword(salt: String, hash: String, version: Int) {
        sp.edit().putString("pw_salt", salt).putString("pw_hash", hash).putInt("pw_v", version).apply()
    }

    var pwFallos: Int
        get() = sp.getInt("pw_fallos", 0)
        set(v) { sp.edit().putInt("pw_fallos", v).apply() }

    var pwBloqueadoHasta: Long
        get() = sp.getLong("pw_bloq_hasta", 0L)
        set(v) { sp.edit().putLong("pw_bloq_hasta", v).apply() }

    // ---- Tiempo de pantalla ----
    var monitorOn: Boolean
        get() = sp.getBoolean("monitor_on", true)
        set(v) { sp.edit().putBoolean("monitor_on", v).apply() }

    fun limites(): List<AppLimit> {
        val txt = sp.getString("limites_json", null) ?: return emptyList()
        return try {
            AppLimit.listFromJson(txt)
        } catch (e: Exception) {
            Log.w("Store", "Límites ilegibles", e)
            emptyList()
        }
    }

    fun saveLimites(list: List<AppLimit>) {
        sp.edit().putString("limites_json", AppLimit.listToJson(list)).apply()
    }

    /** Minutos extra concedidos hoy a una app (por resolver un puzzle). */
    fun extraMin(pkg: String): Int = sp.getInt("extra_${LocalDate.now()}_$pkg", 0)
    fun addExtraMin(pkg: String, min: Int) {
        sp.edit().putInt("extra_${LocalDate.now()}_$pkg", extraMin(pkg) + min).apply()
    }

    fun resumenTime(): LocalTime = parseHm(resumenHora) ?: LocalTime.of(8, 0)

    // ---- Banderas sueltas (avisos ya enviados, etc.) ----
    fun flag(key: String): Boolean = sp.getBoolean("f_$key", false)
    fun setFlag(key: String, v: Boolean) {
        sp.edit().putBoolean("f_$key", v).apply()
    }

    // ---- Google Sheets ----
    var sheetsUrl: String
        get() = sp.getString("sheets_url", "") ?: ""
        set(v) { sp.edit().putString("sheets_url", v.trim()).apply() }

    var sheetsToken: String
        get() = sp.getString("sheets_token", "") ?: ""
        set(v) { sp.edit().putString("sheets_token", v.trim()).apply() }

    var sheetsAuto: Boolean
        get() = sp.getBoolean("sheets_auto", true)
        set(v) { sp.edit().putBoolean("sheets_auto", v).apply() }

    var sheetsUltimo: String
        get() = sp.getString("sheets_ultimo", "") ?: ""
        set(v) { sp.edit().putString("sheets_ultimo", v).apply() }

    // ---- Mantenimiento ----
    private val fechaEnClave = Regex("\\d{4}-\\d{2}-\\d{2}")

    /**
     * Borra lo que solo sirve para un día concreto y ya pasó (minutos extra, avisos enviados).
     * Así el archivo de ajustes no crece con los meses.
     */
    fun purgar(hoy: LocalDate = LocalDate.now()) {
        val e = sp.edit()
        var n = 0
        for (k in sp.all.keys) {
            if (!(k.startsWith("extra_") || k.startsWith("f_av") || k.startsWith("f_perm"))) continue
            val m = fechaEnClave.find(k) ?: continue
            val d = try { LocalDate.parse(m.value) } catch (ex: Exception) { continue }
            if (d.isBefore(hoy)) {
                e.remove(k)
                n++
            }
        }
        // Marcas "false" que dejaba la versión anterior.
        for ((k, v) in sp.all) {
            if ((k.startsWith("b_") || k.startsWith("t_")) && v == false) {
                e.remove(k)
                n++
            }
        }
        if (n > 0) e.apply()
    }

    // ---- Respaldo (para exportar / Google Sheets) ----
    private fun protegida(k: String): Boolean =
        k.startsWith("pw_") || k.startsWith("sheets_") || k.startsWith("f_") ||
            k.startsWith("extra_") || k == "ultimo_aviso_ms" || k.startsWith("horario_danado")

    /** Foto de tu progreso y ajustes (sin contraseña ni datos de Sheets). */
    fun exportar(): JSONObject {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            if (protegida(k)) continue
            val e = JSONObject()
            when (v) {
                is Boolean -> e.put("t", "b").put("v", v)
                is Int -> e.put("t", "i").put("v", v)
                is Long -> e.put("t", "l").put("v", v)
                is Float -> e.put("t", "f").put("v", v.toDouble())
                is String -> e.put("t", "s").put("v", v)
                else -> continue
            }
            o.put(k, e)
        }
        return o
    }

    /** Reemplaza el progreso y los ajustes por los de la copia. Mantiene tu contraseña y tus datos de Sheets. */
    fun importar(o: JSONObject) {
        val e = sp.edit()
        for (k in sp.all.keys) {
            if (!protegida(k)) e.remove(k)
        }
        val claves = o.keys()
        while (claves.hasNext()) {
            val k = claves.next()
            if (protegida(k)) continue
            val x = o.optJSONObject(k) ?: continue
            when (x.optString("t")) {
                "b" -> e.putBoolean(k, x.getBoolean("v"))
                "i" -> e.putInt(k, x.getInt("v"))
                "l" -> e.putLong(k, x.getLong("v"))
                "f" -> e.putFloat(k, x.getDouble("v").toFloat())
                "s" -> e.putString(k, x.getString("v"))
            }
        }
        e.commit()
    }
}

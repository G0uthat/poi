package com.plan.ingsoft

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate

/** Suena la alarma: muestra TODOS los avisos pendientes y programa el siguiente. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Notifier.dispararPendientes(context)
        } catch (e: Exception) {
            Log.w("AlarmReceiver", "No se pudo mostrar un aviso", e)
        }
        Notifier.scheduleNext(context)
        PlanWidget.updateAll(context)
    }
}

/** Botones de las notificaciones: "Hecho", "Hito listo" y "Deshacer". */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val st = Store(context)
        when (intent.getStringExtra("kind")) {
            "block" -> {
                val fecha = intent.getStringExtra("date")
                if (fecha != null) {
                    st.setBlock(LocalDate.parse(fecha), intent.getIntExtra("idx", 0), true)
                }
            }
            "hito" -> {
                // Se marca el hito que mostraba el aviso, no "el siguiente de ahora".
                val id = intent.getStringExtra("hitoId")
                if (id != null) {
                    st.setHito(id, true)
                } else {
                    val pista = intent.getStringExtra("pista") ?: "A"
                    PlanLoader.get(context).siguienteHito(st, pista)?.let { st.setHito(it.id, true) }
                }
            }
            "undo_hito" -> {
                intent.getStringExtra("hitoId")?.let { st.setHito(it, false) }
            }
        }
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra("nid", 0))
        PlanWidget.updateAll(context)
        SheetsSync.pedirSincronizacion(context)
    }
}

/** Reinicio, cambio de hora/zona o actualización: reprograma avisos y vigilancia. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.createChannels(context)
        Notifier.scheduleNext(context)
        MonitorService.sync(context)
        PlanWidget.updateAll(context)
        SheetsSync.programar(context)
    }
}

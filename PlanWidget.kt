package com.plan.ingsoft

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDateTime

class PlanWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_HITO) {
            val st = Store(context)
            val id = intent.getStringExtra("hitoId")
            // Se marca exactamente el hito que mostraba el widget, y se avisa con opción de deshacer.
            val hito = PlanLoader.get(context).hitos.firstOrNull { it.id == id }
            if (hito != null && !st.isHitoDone(hito.id)) {
                st.setHito(hito.id, true)
                Notifier.avisoHitoMarcado(context, hito)
                SheetsSync.pedirSincronizacion(context)
            }
            updateAll(context)
        }
    }

    companion object {
        const val ACTION_HITO = "com.plan.ingsoft.WIDGET_HITO"

        fun updateAll(context: Context) {
            val app = context.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, PlanWidget::class.java))
            for (id in ids) mgr.updateAppWidget(id, build(app))
        }

        private fun botonHito(ctx: Context, rv: RemoteViews, viewId: Int, rc: Int, h: Hito?) {
            if (h == null) {
                rv.setViewVisibility(viewId, View.GONE)
                return
            }
            rv.setViewVisibility(viewId, View.VISIBLE)
            val pi = PendingIntent.getBroadcast(
                ctx, rc,
                Intent(ctx, PlanWidget::class.java).setAction(ACTION_HITO).putExtra("hitoId", h.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(viewId, pi)
        }

        private fun build(ctx: Context): RemoteViews {
            val plan = PlanLoader.get(ctx)
            val st = Store(ctx)
            val now = LocalDateTime.now()
            val hoy = now.toLocalDate()
            val rv = RemoteViews(ctx.packageName, R.layout.widget_plan)

            val a = plan.siguienteHito(st, "A")
            val b = plan.siguienteHito(st, "B")
            rv.setTextViewText(R.id.w_next_block, plan.textoBloque(now))
            rv.setTextViewText(
                R.id.w_hito_a,
                "► A · " + (a?.let { "${it.id} ${it.texto}" } ?: "✔ pista completada")
            )
            rv.setTextViewText(
                R.id.w_hito_b,
                "► B · " + (b?.let { "${it.id} ${it.texto}" } ?: "✔ pista completada")
            )

            val total = plan.horasTotal()
            val pct = if (total > 0) (plan.horasHechas(st) / total * 100).toInt() else 0
            rv.setProgressBar(R.id.w_progress, 100, pct, false)
            rv.setTextViewText(R.id.w_status, "$pct% · " + plan.estadoGlobal(st, hoy))

            val open = PendingIntent.getActivity(
                ctx, 10,
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_root, open)

            botonHito(ctx, rv, R.id.w_btn_hito, 11, a)
            botonHito(ctx, rv, R.id.w_btn_hito_b, 12, b)
            return rv
        }
    }
}

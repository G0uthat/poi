package com.plan.ingsoft

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

private val ALTO_HORA = 56.dp
private val ANCHO_HORAS = 36.dp
private val INICIALES = listOf("L", "M", "X", "J", "V", "S", "D")

/**
 * Horario en pantalla completa, como la vista de semana / día de un calendario.
 * Toca un bloque para ver el detalle y marcarlo como hecho.
 */
@Composable
fun HorarioScreen(
    plan: Plan, st: Store, tick: Int, bump: () -> Unit,
    completa: Boolean, onCompleta: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hoy = LocalDate.now()
    var modo by remember { mutableStateOf(0) } // 0 = semana, 1 = día
    var semana by remember { mutableStateOf(0) } // desplazamiento en semanas respecto a esta
    var dia by remember { mutableStateOf(hoy) }
    var detalle by remember { mutableStateOf<Pair<LocalDate, Block>?>(null) }
    var estadoHoja by remember { mutableStateOf("") }
    var leyendo by remember { mutableStateOf(false) }

    val lunes = hoy.minusDays((hoy.dayOfWeek.value - 1).toLong()).plusWeeks(semana.toLong())
    val columnas: List<LocalDate> =
        if (modo == 0) (0 until 7).map { lunes.plusDays(it.toLong()) } else listOf(dia)
    val titulo = if (modo == 0) {
        val fin = lunes.plusDays(6)
        "${lunes.dayOfMonth} ${lunes.format(MES_CORTO)} – ${fin.dayOfMonth} ${fin.format(MES_CORTO)}"
    } else {
        cap(dia.format(FMT_LARGO))
    }
    val conHoja = st.sheetsHorarioId.isNotBlank()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", color = Dc.text, fontSize = 26.sp, modifier = Modifier
                .clickable { if (modo == 0) semana -= 1 else dia = dia.minusDays(1) }
                .padding(horizontal = 10.dp))
            Text(
                titulo, color = Dc.text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).clickable { semana = 0; dia = hoy },
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text("›", color = Dc.text, fontSize = 26.sp, modifier = Modifier
                .clickable { if (modo == 0) semana += 1 else dia = dia.plusDays(1) }
                .padding(horizontal = 10.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pill("Semana", Dc.blurple, modo == 0) { modo = 0 }
            Pill("Día", Dc.blurple, modo == 1) {
                modo = 1
                if (dia.isBefore(lunes) || dia.isAfter(lunes.plusDays(6))) dia = lunes
            }
            Spacer(Modifier.weight(1f))
            if (conHoja) {
                Pill(if (leyendo) "…" else "↻ Hoja", Dc.green, false) {
                    if (!leyendo) {
                        leyendo = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { HorarioSheet.traer(ctx) }
                            estadoHoja = r.mensaje
                            if (r.cambio) bump()
                            leyendo = false
                        }
                    }
                }
            }
            Pill(if (completa) "✕ Salir" else "⛶ Completa", Dc.blurple, false) { onCompleta(!completa) }
        }
        if (estadoHoja.isNotBlank()) {
            Text(
                estadoHoja, color = Dc.muted, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Cabecera con los días
        Row(Modifier.fillMaxWidth().padding(start = ANCHO_HORAS, top = 6.dp, bottom = 4.dp)) {
            for (d in columnas) {
                val esHoy = d == hoy
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(INICIALES[d.dayOfWeek.value - 1], color = Dc.dim, fontSize = 11.sp)
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(if (esHoy) Dc.blurple else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${d.dayOfMonth}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (esHoy) Color.White else Dc.text
                        )
                    }
                }
            }
        }

        Rejilla(Modifier.weight(1f).fillMaxWidth(), columnas, plan, st, hoy) { d, b -> detalle = d to b }
    }

    val sel = detalle
    if (sel != null) {
        val d = sel.first
        val b = sel.second
        val hecho = b.checkable() && st.isBlockDone(d, b.id)
        val hito = if (b.pilar == "Estudio") plan.hitoDeBloque(st, b) else null
        AlertDialog(
            onDismissRequest = { detalle = null },
            title = { Text(b.act, maxLines = 4, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(cap(d.format(FMT_LARGO)), color = Dc.muted, fontSize = 13.sp)
                    Text("${b.ini}–${b.fin} · ${fmtDur(b.finMin - b.iniMin)}", color = Dc.text)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(b.pilar, pilarColor(b.pilar))
                        if (!b.notif) {
                            Spacer(Modifier.width(8.dp))
                            Text("sin aviso", color = Dc.dim, fontSize = 12.sp)
                        }
                    }
                    if (b.lugar.isNotBlank()) Text("📍 ${b.lugar}", color = Dc.text)
                    if (hito != null) {
                        Text("Toca: ${hito.id} ${hito.texto}", color = Dc.text, fontSize = 13.sp)
                        Text("${hito.curso} · ${hito.horas} h", color = Dc.dim, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                if (b.checkable()) {
                    TextButton(onClick = {
                        st.setBlock(d, b.id, !hecho)
                        bump()
                        detalle = null
                    }) { Text(if (hecho) "Desmarcar" else "Marcar hecho") }
                }
            },
            dismissButton = { TextButton(onClick = { detalle = null }) { Text("Cerrar") } }
        )
    }
}

private val MES_CORTO = java.time.format.DateTimeFormatter.ofPattern("MMM", LOCALE_ES)

@Composable
private fun Rejilla(
    modifier: Modifier, columnas: List<LocalDate>, plan: Plan, st: Store, hoy: LocalDate,
    onBloque: (LocalDate, Block) -> Unit
) {
    val semanal = columnas.size > 1
    val todos = columnas.flatMap { plan.bloquesDe(it) }
    val h0 = ((todos.minOfOrNull { it.iniMin } ?: 360) / 60).coerceIn(0, 22)
    val h1 = (((todos.maxOfOrNull { minOf(it.finMin, 1440) } ?: 1320) + 59) / 60).coerceIn(h0 + 1, 24)
    val scroll = rememberScrollState()
    val densidad = LocalDensity.current
    val ahora = LocalDateTime.now()

    // Al abrir, baja hasta la hora actual.
    LaunchedEffect(h0, columnas.size) {
        val hora = if (columnas.contains(hoy)) ahora.hour else h0
        val px = with(densidad) { ALTO_HORA.toPx() }
        scroll.scrollTo(((hora - h0 - 1).coerceAtLeast(0) * px).toInt())
    }

    BoxWithConstraints(modifier) {
        val anchoTotal = maxWidth
        val colW = (anchoTotal - ANCHO_HORAS) / columnas.size
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(ALTO_HORA * (h1 - h0))) {
                // Líneas y horas
                for (h in h0..h1) {
                    val y = ALTO_HORA * (h - h0)
                    Box(
                        Modifier
                            .offset(x = ANCHO_HORAS, y = y)
                            .width(anchoTotal - ANCHO_HORAS)
                            .height(1.dp)
                            .background(Dc.hover.copy(alpha = 0.6f))
                    )
                    Text(
                        "%02d".format(h % 24), color = Dc.dim, fontSize = 10.sp,
                        modifier = Modifier.offset(x = 6.dp, y = if (h == h0) y else y - 7.dp)
                    )
                }
                // Bloques de cada día
                columnas.forEachIndexed { i, d ->
                    val x = ANCHO_HORAS + colW * i
                    if (semanal && d == hoy) {
                        Box(
                            Modifier
                                .offset(x = x, y = 0.dp)
                                .size(colW, ALTO_HORA * (h1 - h0))
                                .background(Dc.blurple.copy(alpha = 0.08f))
                        )
                    }
                    for (b in plan.bloquesDe(d)) {
                        val ini = b.iniMin
                        val fin = minOf(b.finMin, 1440)
                        if (fin <= h0 * 60 || ini >= h1 * 60) continue
                        val y = ALTO_HORA * ((ini - h0 * 60) / 60f)
                        val alto = (ALTO_HORA * ((fin - ini) / 60f) - 2.dp).coerceAtLeast(12.dp)
                        val hecho = b.checkable() && st.isBlockDone(d, b.id)
                        val color = pilarColor(b.pilar)
                        Box(
                            Modifier
                                .offset(x = x + 1.dp, y = y + 1.dp)
                                .size(colW - 2.dp, alto)
                                .clip(RoundedCornerShape(6.dp))
                                .background(color.copy(alpha = if (hecho) 0.35f else 0.92f))
                                .clickable { onBloque(d, b) }
                                .padding(if (semanal) 3.dp else 8.dp)
                        ) {
                            if (semanal) {
                                val lineas = ((alto.value - 6f) / 11f).toInt().coerceIn(1, 8)
                                Text(
                                    b.act, color = Color.White, fontSize = 9.sp, lineHeight = 10.sp,
                                    maxLines = lineas, overflow = TextOverflow.Ellipsis,
                                    textDecoration = if (hecho) TextDecoration.LineThrough else null
                                )
                            } else {
                                val hito = if (b.pilar == "Estudio" && b.esGenerica) plan.hitoDeBloque(st, b) else null
                                val texto = if (hito != null) "${hito.id} ${hito.texto}" else b.act
                                Column {
                                    Text(
                                        "${b.ini}–${b.fin}", color = Color.White, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        texto, color = Color.White, fontSize = 14.sp, maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        textDecoration = if (hecho) TextDecoration.LineThrough else null
                                    )
                                    if (b.lugar.isNotBlank() && alto > 60.dp) {
                                        Text("📍 ${b.lugar}", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                // Línea de la hora actual
                val iHoy = columnas.indexOf(hoy)
                val minAhora = ahora.hour * 60 + ahora.minute
                if (iHoy >= 0 && minAhora >= h0 * 60 && minAhora <= h1 * 60) {
                    Box(
                        Modifier
                            .offset(x = ANCHO_HORAS + colW * iHoy, y = ALTO_HORA * ((minAhora - h0 * 60) / 60f))
                            .size(colW, 2.dp)
                            .background(Dc.red)
                    )
                }
            }
        }
    }
}

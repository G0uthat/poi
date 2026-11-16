package com.plan.ingsoft

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime

private val LETRAS_DIA = listOf("L", "M", "X", "J", "V", "S", "D")

/** Pantalla principal: solo lo que toca hacer hoy, con chequeo rápido. */
@Composable
fun HoyScreen(plan: Plan, st: Store, tick: Int, bump: () -> Unit, onSettings: () -> Unit) {
    val hoy = LocalDate.now()
    var sel by remember { mutableStateOf(hoy) }
    val lunes = hoy.minusDays((hoy.dayOfWeek.value - 1).toLong())
    val bloques = plan.bloquesDe(sel)
    val checks = bloques.filter { it.checkable() }
    val hechos = checks.count { st.isBlockDone(sel, it.id) }
    val frac = if (checks.isEmpty()) 0f else hechos.toFloat() / checks.size
    val now = LocalDateTime.now()
    val esHoy = sel == hoy
    val actual = if (esHoy) plan.bloqueActual(now) else null
    val racha = plan.racha(st, hoy)
    val hA = plan.siguienteHito(st, "A")
    val hB = plan.siguienteHito(st, "B")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (esHoy) "Hoy" else cap(sel.format(FMT_CORTO)),
                        color = Dc.text, fontSize = 28.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        cap(sel.format(FMT_LARGO)) + if (esHoy && racha > 0) "  ·  🔥 $racha" else "",
                        color = Dc.muted, fontSize = 13.sp
                    )
                }
                Ring(frac, "$hechos/${checks.size}")
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Dc.card)
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) { Text("⚙️", fontSize = 18.sp) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    val d = lunes.plusDays(i.toLong())
                    DayChip(
                        Modifier.weight(1f), LETRAS_DIA[i], d.dayOfMonth,
                        d == sel, plan.cumplido(st, d)
                    ) { sel = d }
                }
            }
        }
        if (plan.inicio.isAfter(hoy)) {
            item {
                Panel {
                    Text(
                        "El plan empieza el ${plan.inicio.format(FMT_CORTO)}",
                        color = Dc.text, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Mientras tanto puedes ver y ajustar tu agenda.",
                        color = Dc.muted, fontSize = 13.sp
                    )
                }
            }
        }
        if (plan.terminado(hoy)) {
            item {
                Panel {
                    Text(
                        "🎉 ¡Completaste las 12 semanas del plan!",
                        color = Dc.text, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Para empezar otro ciclo, cambia la fecha de inicio en Ajustes (⚙️).",
                        color = Dc.muted, fontSize = 13.sp
                    )
                }
            }
        }
        if (esHoy) {
            item {
                Panel {
                    SectionTitle("Hoy toca (estudio)")
                    HitoRow("A", hA) { hA?.let { st.setHito(it.id, true); bump() } }
                    if (hB != null) HitoRow("B", hB) { st.setHito(hB.id, true); bump() }
                }
            }
        }
        item { SectionTitle("Agenda") }
        if (bloques.isEmpty()) {
            item { Text("Sin bloques este día.", color = Dc.muted) }
        }
        items(bloques, key = { it.id }) { b ->
            val hito = plan.hitoDeBloque(st, b)
            val titulo = if (b.pilar == "Estudio" && b.esGenerica && hito != null) {
                "${hito.id} ${hito.texto}"
            } else {
                b.act
            }
            val sub = buildString {
                append("${b.ini}–${b.fin} · ${fmtDur(b.finMin - b.iniMin)}")
                if (b.pilar == "Estudio" && hito != null) append(" · ${hito.curso}")
                if (b.lugar.isNotBlank()) append(" · 📍${b.lugar}")
            }
            AgendaRow(
                b, titulo, sub, st.isBlockDone(sel, b.id), actual?.id == b.id
            ) {
                st.setBlock(sel, b.id, !st.isBlockDone(sel, b.id))
                bump()
            }
        }
    }
}

@Composable
fun DayChip(
    modifier: Modifier, letra: String, num: Int, selected: Boolean, cumplido: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Dc.blurple else Dc.card)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(letra, color = if (selected) Color.White else Dc.dim, fontSize = 11.sp)
        Text(
            "$num", color = if (selected) Color.White else Dc.text,
            fontSize = 16.sp, fontWeight = FontWeight.Bold
        )
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(if (cumplido) Dc.green else Color.Transparent)
        )
    }
}

@Composable
fun HitoRow(pista: String, h: Hito?, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill("Pista $pista", if (pista == "A") Dc.blurple else Color(0xFF8A4FD6))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (h == null) {
                Text("✔ Pista completada", color = Dc.green)
            } else {
                Text(
                    "${h.id} ${h.texto}", color = Dc.text, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text("${h.curso} · ${h.horas} h", color = Dc.dim, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (h != null) CheckCircle(false, Dc.green, onDone)
    }
}

@Composable
fun AgendaRow(b: Block, titulo: String, sub: String, done: Boolean, ahora: Boolean, onToggle: () -> Unit) {
    val color = pilarColor(b.pilar)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            b.ini.toString(), color = Dc.muted, fontSize = 12.sp,
            modifier = Modifier.width(46.dp).padding(top = 14.dp)
        )
        if (b.checkable()) {
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(Dc.card)
                    .then(if (ahora) Modifier.border(1.5.dp, Dc.blurple, shape) else Modifier)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(color))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.pilar.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (ahora) {
                            Spacer(Modifier.width(6.dp))
                            Pill("AHORA", Dc.blurple)
                        }
                    }
                    Text(
                        titulo,
                        color = if (done) Dc.dim else Dc.text,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                    Text(sub, color = Dc.dim, fontSize = 12.sp)
                }
                CheckCircle(done, color, onToggle)
            }
        } else {
            Text(
                b.act, color = Dc.dim, fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(top = 14.dp, start = 4.dp)
            )
        }
    }
}

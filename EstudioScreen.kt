package com.plan.ingsoft

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import java.time.LocalDate

/** Estudio: pistas, cursos y hitos (al estilo Notion, con chequeo rápido). */
@Composable
fun EstudioScreen(plan: Plan, st: Store, tick: Int, bump: () -> Unit) {
    val hoy = LocalDate.now()
    var pista by remember { mutableStateOf("A") }
    var abierto by remember { mutableStateOf<String?>(null) }
    val estados = plan.timeline(st, hoy).filter { it.curso.pista == pista }
    val total = plan.horasTotal()
    val hechas = plan.horasHechas(st)
    val frac = if (total > 0) (hechas / total).toFloat() else 0f
    val hA = plan.siguienteHito(st, "A")
    val hB = plan.siguienteHito(st, "B")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text("Estudio", color = Dc.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(plan.estadoGlobal(st, hoy), color = Dc.muted, fontSize = 13.sp)
            }
        }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Stat("Hitos", "${plan.hitosHechos(st)}/${plan.hitos.size}", Modifier.weight(1f))
                    Stat("Esta semana", "${plan.hitosSemana(st, hoy)}", Modifier.weight(1f))
                    Stat("Horas", "${"%.0f".format(hechas)}/${"%.0f".format(total)}", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Bar(frac, Dc.blurple)
            }
        }
        item {
            Panel {
                SectionTitle("Siguiente hito")
                HitoRow("A", hA) { hA?.let { st.setHito(it.id, true); bump() } }
                if (hB != null) HitoRow("B", hB) { st.setHito(hB.id, true); bump() }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Pista A · principal", Dc.blurple, pista == "A") { pista = "A"; abierto = null }
                Pill("Pista B · matemática", Color(0xFF8A4FD6), pista == "B") { pista = "B"; abierto = null }
            }
        }
        items(estados, key = { it.curso.nombre }) { e ->
            val expandido = abierto == e.curso.nombre
            val hechosC = e.hitos.count { st.isHitoDone(it.id) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Dc.card)
                    .clickable { abierto = if (expandido) null else e.curso.nombre }
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            e.curso.nombre, color = Dc.text, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${e.curso.ciclo} · ${e.inicio.format(FMT_FECHA)} → ${e.fin.format(FMT_FECHA)}",
                            color = Dc.dim, fontSize = 12.sp
                        )
                    }
                    Text(
                        "${(e.pctReal * 100).toInt()}%", color = Dc.text,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Bar(e.pctReal.toFloat(), if (e.pctReal >= 1.0) Dc.green else Dc.blurple)
                Spacer(Modifier.height(4.dp))
                Text("$hechosC/${e.hitos.size} hitos · ${e.estado}", color = Dc.muted, fontSize = 12.sp)
                if (expandido) {
                    if (e.curso.fuentes.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("📖 ${e.curso.fuentes}", color = Dc.dim, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    for (h in e.hitos) {
                        val ok = st.isHitoDone(h.id)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${h.id}  ${h.texto}",
                                    color = if (ok) Dc.dim else Dc.text, fontSize = 14.sp,
                                    textDecoration = if (ok) TextDecoration.LineThrough else null
                                )
                                Text(
                                    "${h.horas} h" + (if (h.donde.isNotEmpty()) " · ${h.donde}" else "") +
                                        (if (ok) " · ✓ " + LocalDate.ofEpochDay(st.hitoFecha(h.id)).format(FMT_FECHA) else ""),
                                    color = Dc.dim, fontSize = 11.sp
                                )
                            }
                            CheckCircle(ok, Dc.green) {
                                st.setHito(h.id, !ok)
                                bump()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Stat(label: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(valor, color = Dc.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Dc.dim, fontSize = 11.sp)
    }
}

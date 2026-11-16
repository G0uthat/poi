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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

/** Progreso: un apartado para Estudio y otro para Ejercicio. */
@Composable
fun ProgresoScreen(plan: Plan, st: Store, tick: Int, bump: () -> Unit) {
    var seg by remember { mutableStateOf(0) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text("Progreso", color = Dc.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("📚 Estudio", Dc.blurple, seg == 0) { seg = 0 }
                    Pill("💪 Ejercicio", Dc.green, seg == 1) { seg = 1 }
                }
            }
        }
        if (seg == 0) progresoEstudio(plan, st)
        else progresoEjercicio(plan, st, bump)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.progresoEstudio(plan: Plan, st: Store) {
    val hoy = LocalDate.now()
    val total = plan.horasTotal()
    val hechas = plan.horasHechas(st)
    val esperadas = plan.horasEsperadas(hoy)
    val frac = if (total > 0) (hechas / total).toFloat() else 0f
    val estados = plan.timeline(st, hoy)
    item {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(frac * 100).toInt()}%", color = Dc.text, fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(10.dp))
                Text("del plan completado", color = Dc.muted)
            }
            Spacer(Modifier.height(8.dp))
            Bar(frac, Dc.blurple)
            Spacer(Modifier.height(8.dp))
            Text(
                "${"%.1f".format(hechas)} h hechas · ${"%.1f".format(esperadas)} h esperadas hoy",
                color = Dc.muted, fontSize = 13.sp
            )
            Text(plan.estadoGlobal(st, hoy), color = Dc.text, fontWeight = FontWeight.SemiBold)
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Panel(Modifier.weight(1f)) { Stat("Hitos hechos", "${plan.hitosHechos(st)}/${plan.hitos.size}") }
            Panel(Modifier.weight(1f)) { Stat("Últimos 7 días", "${plan.hitosSemana(st, hoy)}") }
            Panel(Modifier.weight(1f)) { Stat("Racha", "🔥 ${plan.racha(st, hoy)}") }
        }
    }
    val fin = estados.maxByOrNull { it.fin }
    if (fin != null) {
        item {
            Panel {
                SectionTitle("Fin estimado del plan")
                Text(
                    fin.fin.format(FMT_FECHA), color = Dc.text, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("al ritmo actual del horario", color = Dc.dim, fontSize = 12.sp)
            }
        }
    }
    item { SectionTitle("Por curso") }
    items(estados, key = { "p_" + it.curso.nombre }) { e ->
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.curso.nombre, color = Dc.text, fontSize = 13.sp, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("${(e.pctReal * 100).toInt()}%", color = Dc.muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Bar(e.pctReal.toFloat(), if (e.pctReal >= 1.0) Dc.green else Dc.blurple)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.progresoEjercicio(
    plan: Plan, st: Store, bump: () -> Unit
) {
    val hechas = (1..12).sumOf { w -> (0 until 5).count { st.isTrainDone(w, it) } }
    val completas = (1..12).count { w -> (0 until 5).all { st.isTrainDone(w, it) } }
    val frac = hechas / 60f
    item {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(frac * 100).toInt()}%", color = Dc.text, fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(10.dp))
                Text("de las 60 sesiones", color = Dc.muted)
            }
            Spacer(Modifier.height(8.dp))
            Bar(frac, Dc.green)
            Spacer(Modifier.height(8.dp))
            Text("$hechas sesiones hechas · $completas semanas completas", color = Dc.muted, fontSize = 13.sp)
        }
    }
    item {
        Panel {
            SectionTitle("Constancia por semana")
            Spacer(Modifier.height(8.dp))
            for (w in 1..12) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("S$w", color = Dc.dim, fontSize = 11.sp, modifier = Modifier.width(30.dp))
                    for (i in 0 until 5) {
                        Box(
                            Modifier
                                .padding(end = 6.dp)
                                .size(22.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (st.isTrainDone(w, i)) Dc.green else Dc.hover)
                        )
                    }
                }
            }
        }
    }
    item { SectionTitle("Test de rendimiento (toca para anotar)") }
    item { TestTabla(st, bump) }
}

@Composable
fun TestTabla(st: Store, bump: () -> Unit) {
    var edit by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var txt by remember { mutableStateOf("") }
    fun valor(p: Int, c: Int): String {
        val v = st.testVal(p, c)
        return if (v.isEmpty() && p == 0 && c == 0) "75" else v
    }
    Panel {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(2.2f))
            for (c in TEST_COLS) {
                Text(c, Modifier.weight(1f), color = Dc.dim, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
            Text("Δ%", Modifier.weight(1f), color = Dc.dim, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        for (p in TESTS.indices) {
            val nums = (0 until 4).mapNotNull { valor(p, it).replace(',', '.').toDoubleOrNull() }
            val cambio = if (nums.size >= 2 && nums.first() != 0.0) {
                (nums.last() - nums.first()) / nums.first() * 100
            } else null
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(TESTS[p], Modifier.weight(2.2f), color = Dc.text, fontSize = 12.sp)
                for (c in 0 until 4) {
                    val v = valor(p, c)
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Dc.hover)
                            .clickable {
                                edit = p to c
                                txt = valor(p, c)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(if (v.isEmpty()) "—" else v, color = Dc.text, fontSize = 13.sp) }
                }
                Text(
                    if (cambio != null) "${if (cambio >= 0) "+" else ""}${"%.0f".format(cambio)}%" else "—",
                    Modifier.weight(1f), color = Dc.green, fontSize = 12.sp, textAlign = TextAlign.Center
                )
            }
        }
    }
    val e = edit
    if (e != null) {
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(TESTS[e.first] + " · " + TEST_COLS[e.second]) },
            text = {
                OutlinedTextField(
                    value = txt, onValueChange = { txt = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    st.setTestVal(e.first, e.second, txt.trim())
                    edit = null
                    bump()
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancelar") } }
        )
    }
}

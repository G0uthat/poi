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
import kotlinx.coroutines.delay

/** Workout: la sesión de hoy con chequeo por ejercicio, temporizador de descanso y registro. */
@Composable
fun WorkoutScreen(plan: Plan, st: Store, tick: Int, bump: () -> Unit) {
    val hoy = LocalDate.now()
    val sem = plan.semanaActual(hoy)
    val fase = plan.fase(sem)
    val ses = plan.sesionDelDia(hoy)
    val idx = plan.indiceSesion(hoy)
    val cols = listOf("Lun A", "Mié B", "Vie A", "Sáb", "Dom")
    var rest by remember { mutableStateOf(0) }
    var restKey by remember { mutableStateOf(0) }

    LaunchedEffect(restKey) {
        while (rest > 0) {
            delay(1000)
            rest -= 1
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text("Workout", color = Dc.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Semana $sem de 12 · Fase $fase de 3", color = Dc.muted, fontSize = 13.sp)
            }
        }
        item {
            Panel {
                SectionTitle("Hoy · " + cap(hoy.format(FMT_CORTO)))
                when (ses) {
                    "A", "B" -> {
                        val lista = plan.ejerciciosDe(ses)
                        Text(
                            "Calistenia · sesión $ses", color = Dc.text, fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        for (e in lista) {
                            val key = "ex_${hoy}_${e.nombre}"
                            val ok = st.flag(key)
                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        e.nombre, color = if (ok) Dc.dim else Dc.text, fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        textDecoration = if (ok) TextDecoration.LineThrough else null
                                    )
                                    Text(
                                        "${e.grupo} · ${e.serie(fase)} · descanso ${e.descanso}",
                                        color = Dc.muted, fontSize = 12.sp
                                    )
                                    Text(
                                        "↓ ${e.facil}   ↑ ${e.dificil}",
                                        color = Dc.dim, fontSize = 11.sp
                                    )
                                }
                                CheckCircle(ok, Dc.green) {
                                    st.setFlag(key, !ok)
                                    val todos = lista.all { x -> st.flag("ex_${hoy}_${x.nombre}") }
                                    if (idx != null) st.setTrain(sem, idx, todos)
                                    bump()
                                }
                            }
                        }
                    }
                    "CAMINATA" -> Text(
                        "Caminata rápida 30–40 min + estiramientos (recuperación activa)",
                        color = Dc.text, modifier = Modifier.padding(top = 4.dp)
                    )
                    "MOVILIDAD" -> Text(
                        "Movilidad suave 10–15 min (sin entrenamiento fuerte)",
                        color = Dc.text, modifier = Modifier.padding(top = 4.dp)
                    )
                    else -> Text(
                        "Día de descanso. Recupérate.", color = Dc.text,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (idx != null && ses != "A" && ses != "B") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Marcar como hecho", color = Dc.muted, modifier = Modifier.weight(1f))
                        CheckCircle(st.isTrainDone(sem, idx), Dc.green) {
                            st.setTrain(sem, idx, !st.isTrainDone(sem, idx))
                            bump()
                        }
                    }
                }
            }
        }
        if (ses == "A" || ses == "B") {
            item {
                Panel {
                    SectionTitle("Descanso entre series")
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (s in listOf(45, 60, 90)) {
                            Pill("${s} s", Dc.blurple, false) {
                                rest = s
                                restKey += 1
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (rest > 0) "$rest s" else "—",
                            color = if (rest > 0) Dc.yellow else Dc.dim,
                            fontSize = 22.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        item { SectionTitle("Registro de sesiones") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(0.9f))
                for (c in cols) {
                    Text(
                        c, Modifier.weight(1f), color = Dc.dim, fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        items((1..12).toList(), key = { it }) { w ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Sem $w", Modifier.weight(0.9f),
                    color = if (w == sem) Dc.text else Dc.muted,
                    fontWeight = if (w == sem) FontWeight.Bold else FontWeight.Normal
                )
                for (i in 0 until 5) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CheckCircle(st.isTrainDone(w, i), Dc.green) {
                            st.setTrain(w, i, !st.isTrainDone(w, i))
                            bump()
                        }
                    }
                }
            }
        }
    }
}

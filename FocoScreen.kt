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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val SOCIALES = listOf(
    "com.zhiliaoapp.musically" to "TikTok",
    "com.ss.android.ugc.trill" to "TikTok",
    "com.instagram.android" to "Instagram",
    "com.facebook.katana" to "Facebook",
    "com.twitter.android" to "X",
    "com.snapchat.android" to "Snapchat",
    "com.google.android.youtube" to "YouTube",
    "com.reddit.frontpage" to "Reddit",
    "com.netflix.mediaclient" to "Netflix",
    "tv.twitch.android.app" to "Twitch"
)

private val COLORES = listOf(
    Color(0xFF5865F2), Color(0xFF23A55A), Color(0xFFD6477F), Color(0xFFF0B232),
    Color(0xFF8A4FD6), Color(0xFF2F9FBF), Color(0xFFE5703A)
)

/** Foco: en qué apps pasas más tiempo, con límites que avisan o bloquean. */
@Composable
fun FocoScreen(st: Store, tick: Int, bump: () -> Unit, pedir: (() -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val acceso = Usage.hasAccess(ctx)
    val overlay = Settings.canDrawOverlays(ctx)
    var uso by remember { mutableStateOf<UsoResumen?>(null) }
    var editando by remember { mutableStateOf<AppUso?>(null) }
    var monitor by remember { mutableStateOf(st.monitorOn) }
    val limites = st.limites()

    LaunchedEffect(tick, acceso) {
        if (acceso) {
            while (true) {
                uso = withContext(Dispatchers.Default) { Usage.resumen(ctx) }
                delay(30_000)
            }
        }
    }

    val u = uso
    val filas = ArrayList<AppUso>()
    if (u != null) filas.addAll(u.apps)
    for (l in limites) if (filas.none { it.pkg == l.pkg }) filas.add(AppUso(l.pkg, l.label, 0L))
    val maxMs = (filas.maxOfOrNull { it.ms } ?: 1L).coerceAtLeast(1L)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text("Foco", color = Dc.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Tu tiempo en pantalla y límites por app", color = Dc.muted, fontSize = 13.sp)
            }
        }
        if (!acceso) {
            item {
                Panel {
                    Text("Necesito acceso al uso de apps", color = Dc.text, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Android pide un permiso especial para saber cuánto usas cada app. " +
                            "Busca \"Plan Ing. Software\" y actívalo.",
                        color = Dc.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Button(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }) { Text("Dar acceso") }
                }
            }
        } else {
            item {
                Panel {
                    SectionTitle("Pantalla hoy")
                    Text(
                        if (u == null) "Calculando…" else fmtMs(u.totalHoyMs),
                        color = Dc.text, fontSize = 34.sp, fontWeight = FontWeight.Bold
                    )
                    if (u != null) {
                        val maxD = (u.semana.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
                        Row(
                            Modifier.fillMaxWidth().height(90.dp).padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            for ((i, par) in u.semana.withIndex()) {
                                val h = (par.second.toFloat() / maxD).coerceIn(0.04f, 1f)
                                Column(
                                    Modifier.weight(1f).fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(h * 0.75f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (i == u.semana.size - 1) Dc.blurple else Dc.hover)
                                    )
                                    Text(
                                        par.first.dayOfWeek.value.let { listOf("L", "M", "X", "J", "V", "S", "D")[it - 1] },
                                        color = Dc.dim, fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (limites.isNotEmpty() && !overlay) {
            item {
                Panel {
                    Text("Para poder bloquear apps", color = Dc.text, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Activa \"Mostrar sobre otras apps\" para que pueda tapar la app cuando llegues al límite.",
                        color = Dc.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Button(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
                        )
                    }) { Text("Permitir") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Vigilancia de límites", color = Dc.text, modifier = Modifier.weight(1f))
                Switch(checked = monitor, onCheckedChange = { v ->
                    monitor = v
                    st.monitorOn = v
                    MonitorService.sync(ctx)
                })
            }
        }
        item { SectionTitle("Apps · toca para poner límite 🔒") }
        if (acceso && u != null && filas.isEmpty()) {
            item { Text("Aún no hay uso registrado hoy.", color = Dc.muted) }
        }
        items(filas, key = { it.pkg }) { a ->
            val lim = limites.firstOrNull { it.pkg == a.pkg }
            val limMs = if (lim != null) (lim.minutos + st.extraMin(a.pkg)) * 60_000L else 0L
            val color = COLORES[Math.abs(a.pkg.hashCode()) % COLORES.size]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Dc.card)
                    .clickable { pedir { editando = a } }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(a.label.take(1).uppercase(), color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label, color = Dc.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(fmtMs(a.ms), color = Dc.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Bar(
                        a.ms.toFloat() / maxMs,
                        if (lim != null && a.ms >= limMs) Dc.red else color
                    )
                }
                Spacer(Modifier.width(10.dp))
                if (lim != null) {
                    val c = if (a.ms >= limMs) Dc.red else if (a.ms >= limMs * 0.8) Dc.yellow else Dc.green
                    Pill((if (lim.bloquear) "🔒 " else "🔔 ") + "${lim.minutos + st.extraMin(a.pkg)} min", c)
                } else {
                    Pill("+ límite", Dc.blurple, false)
                }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    pedir {
                        val pm = ctx.packageManager
                        val nuevos = ArrayList<AppLimit>(st.limites())
                        for ((pkg, nombre) in SOCIALES) {
                            if (pm.getLaunchIntentForPackage(pkg) != null && nuevos.none { it.pkg == pkg }) {
                                nuevos.add(AppLimit(pkg, nombre, 45, true))
                            }
                        }
                        st.saveLimites(nuevos)
                        MonitorService.sync(ctx)
                        bump()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sugerir 45 min para redes sociales instaladas 🔒") }
        }
    }

    val e = editando
    if (e != null) {
        val lim = limites.firstOrNull { it.pkg == e.pkg }
        var minutos by remember(e.pkg) { mutableStateOf(lim?.minutos ?: 30) }
        var bloquear by remember(e.pkg) { mutableStateOf(lim?.bloquear ?: true) }
        AlertDialog(
            onDismissRequest = { editando = null },
            title = { Text("Límite diario · ${e.label}") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { minutos = (minutos - 5).coerceAtLeast(5) }) { Text("−5") }
                        Text(
                            "$minutos min", modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(onClick = { minutos = (minutos + 5).coerceAtMost(600) }) { Text("+5") }
                    }
                    Row(
                        Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (m in listOf(15, 30, 45, 60, 90)) {
                            Pill("$m", Dc.blurple, minutos == m) { minutos = m }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (bloquear) "Bloquear al llegar" else "Solo avisar",
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = bloquear, onCheckedChange = { bloquear = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val lista = st.limites().filter { it.pkg != e.pkg } +
                        AppLimit(e.pkg, e.label, minutos, bloquear)
                    st.saveLimites(lista)
                    MonitorService.sync(ctx)
                    editando = null
                    bump()
                }) { Text("Guardar") }
            },
            dismissButton = {
                Row {
                    if (lim != null) {
                        TextButton(onClick = {
                            st.saveLimites(st.limites().filter { it.pkg != e.pkg })
                            MonitorService.sync(ctx)
                            editando = null
                            bump()
                        }) { Text("Quitar", color = Dc.red) }
                    }
                    TextButton(onClick = { editando = null }) { Text("Cancelar") }
                }
            }
        )
    }
}

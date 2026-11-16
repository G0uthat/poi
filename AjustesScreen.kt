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
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.app.NotificationManagerCompat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FilaSwitch(texto: String, valor: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, color = Dc.text, modifier = Modifier.weight(1f))
        Switch(checked = valor, onCheckedChange = onChange)
    }
}

@Composable
fun PermisoRow(texto: String, ok: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, color = Dc.text, modifier = Modifier.weight(1f))
        if (ok) Pill("✔ Activo", Dc.green) else Pill("Activar", Dc.red, true, onClick)
    }
}

@Composable
fun TextoDialog(
    titulo: String, ayuda: String, inicial: String, clave: Boolean = false,
    onGuardar: (String) -> Boolean, onDismiss: () -> Unit
) {
    var txt by remember { mutableStateOf(inicial) }
    var err by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column {
                Text(ayuda, color = Dc.muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = txt, onValueChange = { txt = it; err = false }, singleLine = true,
                    visualTransformation = if (clave) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (err) Text("Valor no válido", color = Dc.red, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (onGuardar(txt.trim())) onDismiss() else err = true }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun AjustesScreen(
    plan: Plan, st: Store, tick: Int, bump: () -> Unit,
    pedir: (() -> Unit) -> Unit, pedirPermiso: () -> Unit,
    onBack: () -> Unit, onEditor: () -> Unit
) {
    val ctx = LocalContext.current
    var activadas by remember { mutableStateOf(st.notifOn) }
    var resumen by remember { mutableStateOf(st.resumen) }
    var preguntar by remember { mutableStateOf(st.askEnd) }
    var pre by remember { mutableStateOf(st.preMin) }
    var hora by remember { mutableStateOf(st.resumenHora) }
    var estados by remember { mutableStateOf(PILARES.associateWith { st.notifPilar(it) }) }
    var dialogo by remember { mutableStateOf(0) }

    val notifOk = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    val alarmOk = Build.VERSION.SDK_INT < 31 ||
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    val usoOk = Usage.hasAccess(ctx)
    val overlayOk = Settings.canDrawOverlays(ctx)

    val scope = rememberCoroutineScope()
    var sheetsUrl by remember { mutableStateOf(st.sheetsUrl) }
    var sheetsToken by remember { mutableStateOf(st.sheetsToken) }
    var sheetsAuto by remember { mutableStateOf(st.sheetsAuto) }
    var sheetsEstado by remember { mutableStateOf(st.sheetsUltimo) }
    var hojaLink by remember { mutableStateOf(st.sheetsHorarioId) }
    var hojaAuto by remember { mutableStateOf(st.sheetsHorarioAuto) }
    var hojaEstado by remember { mutableStateOf(st.sheetsHorarioUltimo) }
    var hojaOcupada by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }

    fun aviso(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()

    val exportar = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        ctx.contentResolver.openOutputStream(uri)?.use {
                            it.write(Respaldo.exportarTexto(st).toByteArray(Charsets.UTF_8))
                        }
                        true
                    } catch (e: Exception) {
                        android.util.Log.w("Ajustes", "No se pudo exportar", e)
                        false
                    }
                }
                aviso(if (ok) "Copia guardada" else "No se pudo guardar la copia")
            }
        }
    }
    val importar = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val error = withContext(Dispatchers.IO) {
                    try {
                        val txt = ctx.contentResolver.openInputStream(uri)?.use {
                            it.readBytes().toString(Charsets.UTF_8)
                        } ?: throw IllegalArgumentException("No se pudo leer el archivo")
                        Respaldo.importarTexto(st, txt)
                        null
                    } catch (e: Exception) {
                        android.util.Log.w("Ajustes", "No se pudo importar", e)
                        e.message ?: "Archivo no válido"
                    }
                }
                if (error == null) {
                    PlanLoader.reload(ctx)
                    Notifier.scheduleNext(ctx)
                    bump()
                    aviso("Copia restaurada")
                } else {
                    aviso("No se pudo restaurar: $error")
                }
            }
        }
    }

    fun reprogramar() {
        Notifier.scheduleNext(ctx)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Dc.card).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) { Text("←", color = Dc.text, fontSize = 18.sp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Ajustes", color = Dc.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (Lock.isUnlocked()) "🔓 Edición desbloqueada" else "🔒 Edición bloqueada",
                        color = Dc.muted, fontSize = 13.sp
                    )
                }
            }
        }

        item { SectionTitle("Notificaciones") }
        item {
            Panel {
                FilaSwitch("Avisos activados", activadas) { v ->
                    activadas = v; st.notifOn = v; reprogramar()
                }
                FilaSwitch("Resumen del día", resumen) { v ->
                    resumen = v; st.resumen = v; reprogramar()
                }
                if (resumen) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (h in listOf("06:30", "07:00", "08:00", "09:00")) {
                            Pill(h, Dc.blurple, hora == h) {
                                hora = h; st.resumenHora = h; reprogramar()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Aviso previo antes de cada bloque", color = Dc.text)
                Row(
                    Modifier.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (m in listOf(0, 5, 10, 15, 30)) {
                        Pill(if (m == 0) "Sin" else "$m min", Dc.blurple, pre == m) {
                            pre = m; st.preMin = m; reprogramar()
                        }
                    }
                }
                FilaSwitch("Preguntar «¿Terminaste?» al acabar", preguntar) { v ->
                    preguntar = v; st.askEnd = v; reprogramar()
                }
            }
        }
        item { SectionTitle("Avisar al empezar bloques de") }
        item {
            Panel {
                for (p in PILARES) {
                    FilaSwitch("${pilarEmoji(p)} $p", estados[p] == true) { v ->
                        estados = estados + (p to v)
                        st.setNotifPilar(p, v)
                        reprogramar()
                    }
                }
                Text(
                    "«Marcar el hito y dormir» siempre avisa. Cada bloque tiene además su propio interruptor en el editor.",
                    color = Dc.dim, fontSize = 12.sp
                )
            }
        }
        item {
            Button(onClick = { Notifier.mostrarPrueba(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("Enviar notificación de prueba")
            }
        }

        item { SectionTitle("Permisos") }
        item {
            Panel {
                PermisoRow("Notificaciones", notifOk) { pedirPermiso() }
                PermisoRow("Alarmas exactas", alarmOk) {
                    if (Build.VERSION.SDK_INT >= 31) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
                        )
                    }
                }
                PermisoRow("Acceso al uso de apps", usoOk) {
                    ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                PermisoRow("Mostrar sobre otras apps", overlayOk) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
                    )
                }
                OutlinedButton(
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) { Text("Quitar ahorro de batería a esta app") }
            }
        }

        item { SectionTitle("Horario desde hoja de Google") }
        item {
            Panel {
                Text(
                    "Edita tu horario en una hoja de Google (pestaña «Horario», opcional «Plan») y la app " +
                        "lo copia sola. La hoja debe estar compartida como «Cualquier persona con el enlace · Lector». " +
                        "Plantillas en google-sheets/LEEME.md.",
                    color = Dc.muted, fontSize = 12.sp
                )
                OutlinedTextField(
                    value = hojaLink,
                    onValueChange = {
                        hojaLink = it
                        st.sheetsHorarioId = HorarioSheet.idDe(it) ?: ""
                        HorarioSheet.programar(ctx)
                    },
                    singleLine = true, label = { Text("Enlace de la hoja") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                FilaSwitch("Actualizar solo (al abrir y cada 3 h)", hojaAuto) { v ->
                    hojaAuto = v; st.sheetsHorarioAuto = v; HorarioSheet.programar(ctx)
                }
                if (hojaEstado.isNotBlank()) Text(hojaEstado, color = Dc.muted, fontSize = 12.sp)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !hojaOcupada && st.sheetsHorarioId.isNotBlank(),
                        onClick = {
                            hojaOcupada = true
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { HorarioSheet.traer(ctx) }
                                hojaEstado = r.mensaje
                                if (r.cambio) bump()
                                hojaOcupada = false
                            }
                        }
                    ) { Text(if (hojaOcupada) "Leyendo…" else "Sincronizar ahora") }
                    OutlinedButton(
                        enabled = st.sheetsHorarioId.isNotBlank(),
                        onClick = {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(HorarioSheet.urlAbrir(st.sheetsHorarioId)))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    ) { Text("Abrir hoja") }
                }
            }
        }

        item { SectionTitle("Google Sheets") }
        item {
            Panel {
                Text(
                    "Envía tu progreso a una hoja de Google (hitos, bloques, entrenos, tests) " +
                        "y guarda un respaldo. Pasos en google-sheets/LEEME.md del proyecto.",
                    color = Dc.muted, fontSize = 12.sp
                )
                OutlinedTextField(
                    value = sheetsUrl,
                    onValueChange = { sheetsUrl = it; st.sheetsUrl = it },
                    singleLine = true, label = { Text("URL del script (termina en /exec)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = sheetsToken,
                    onValueChange = { sheetsToken = it; st.sheetsToken = it },
                    singleLine = true, label = { Text("Token (el mismo del script)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                FilaSwitch("Enviar solo cada vez que cambie algo (y cada 6 h)", sheetsAuto) { v ->
                    sheetsAuto = v; st.sheetsAuto = v; SheetsSync.programar(ctx)
                }
                if (sheetsEstado.isNotBlank()) {
                    Text(sheetsEstado, color = Dc.muted, fontSize = 12.sp)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !ocupado,
                        onClick = {
                            ocupado = true
                            SheetsSync.programar(ctx)
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { SheetsSync.enviar(ctx) }
                                sheetsEstado = r.mensaje
                                ocupado = false
                            }
                        }
                    ) { Text(if (ocupado) "Trabajando…" else "Enviar ahora") }
                    OutlinedButton(
                        enabled = !ocupado,
                        onClick = {
                            pedir {
                                ocupado = true
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { SheetsSync.restaurar(ctx) }
                                    sheetsEstado = r.mensaje
                                    if (r.ok) bump()
                                    ocupado = false
                                }
                            }
                        }
                    ) { Text("Restaurar de la hoja") }
                }
            }
        }

        item { SectionTitle("Copia de seguridad (archivo)") }
        item {
            Panel {
                Text(
                    "Guarda tu progreso y ajustes en un archivo para no perderlos al cambiar de teléfono. " +
                        "No incluye tu contraseña.",
                    color = Dc.muted, fontSize = 12.sp
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportar.launch("plan-ing-software-copia.json") }) { Text("Exportar") }
                    OutlinedButton(
                        onClick = { pedir { importar.launch(arrayOf("application/json", "*/*")) } }
                    ) { Text("Importar") }
                }
            }
        }

        item { SectionTitle("Diagnóstico") }
        item {
            Panel {
                val fallos = ArrayList<String>()
                if (!notifOk) fallos.add("Las notificaciones están desactivadas.")
                if (!alarmOk) fallos.add("Sin alarmas exactas: los avisos pueden llegar con retraso.")
                if (st.limites().isNotEmpty() && !usoOk) fallos.add("Sin acceso al uso: los límites de apps no funcionan.")
                if (st.limites().isNotEmpty() && !overlayOk) fallos.add("Sin «Mostrar sobre otras apps»: no se puede tapar la pantalla.")
                if (st.horarioDanado()) fallos.add("Tu horario personalizado no se pudo leer; se usa el original (hay una copia guardada).")
                if (fallos.isEmpty()) {
                    Text("✔ Todo en orden", color = Dc.green)
                } else {
                    for (f in fallos) Text("⚠ $f", color = Dc.yellow, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        item { SectionTitle("Plan · 🔒 con contraseña o retos") }
        item {
            Panel {
                OutlinedButton(onClick = { pedir { onEditor() } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Editar horario (hora, día, lugar…)")
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Fecha de inicio del plan", color = Dc.text)
                        Text(plan.inicio.format(FMT_FECHA), color = Dc.muted, fontSize = 12.sp)
                    }
                    Pill("Cambiar", Dc.blurple, false) { pedir { dialogo = 1 } }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Eficiencia del tiempo de estudio", color = Dc.text)
                        Text("${(plan.eficiencia * 100).toInt()}%", color = Dc.muted, fontSize = 12.sp)
                    }
                    Pill("Cambiar", Dc.blurple, false) { pedir { dialogo = 2 } }
                }
                OutlinedButton(
                    onClick = { pedir { dialogo = 4 } },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) { Text("Restaurar horario original") }
            }
        }

        item { SectionTitle("Seguridad") }
        item {
            Panel {
                Text(
                    if (Lock.hasPassword(st)) "Contraseña definida" else "Aún no tienes contraseña (solo retos)",
                    color = Dc.text
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pedir { dialogo = 3 } }) {
                        Text(if (Lock.hasPassword(st)) "Cambiar contraseña" else "Crear contraseña")
                    }
                    OutlinedButton(onClick = { Lock.lockNow(); bump() }) { Text("Bloquear ya") }
                }
            }
        }
    }

    when (dialogo) {
        1 -> TextoDialog(
            "Fecha de inicio", "Formato AAAA-MM-DD (por ejemplo 2026-11-16).",
            plan.inicio.toString(), false,
            onGuardar = { t ->
                try {
                    LocalDate.parse(t)
                    st.setInicioOverride(t)
                    PlanLoader.reload(ctx)
                    reprogramar()
                    bump()
                    true
                } catch (e: Exception) {
                    false
                }
            },
            onDismiss = { dialogo = 0 }
        )
        2 -> TextoDialog(
            "Eficiencia", "Entre 0.30 y 1.00. Con 0.85 se cuenta el 85% de tus horas de estudio.",
            plan.eficiencia.toString(), false,
            onGuardar = { t ->
                val v = t.replace(',', '.').toDoubleOrNull()
                if (v != null && v >= 0.3 && v <= 1.0) {
                    st.setEficienciaOverride(v)
                    PlanLoader.reload(ctx)
                    bump()
                    true
                } else {
                    false
                }
            },
            onDismiss = { dialogo = 0 }
        )
        3 -> TextoDialog(
            "Contraseña nueva", "Mínimo 4 caracteres. Sirve para editar el plan y los límites.",
            "", true,
            onGuardar = { t ->
                if (t.length >= 4) {
                    Lock.setPassword(st, t)
                    bump()
                    true
                } else {
                    false
                }
            },
            onDismiss = { dialogo = 0 }
        )
        4 -> AlertDialog(
            onDismissRequest = { dialogo = 0 },
            title = { Text("Restaurar horario") },
            text = { Text("Se borran tus cambios de horario y vuelve el de tu Excel. Tus marcas de progreso se mantienen.") },
            confirmButton = {
                TextButton(onClick = {
                    st.saveHorarioJson(null)
                    PlanLoader.reload(ctx)
                    reprogramar()
                    dialogo = 0
                    bump()
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { dialogo = 0 }) { Text("Cancelar") } }
        )
    }
}

// ------------------------------------------------------------ EDITOR

@Composable
fun HorarioEditorScreen(plan: Plan, st: Store, bump: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var dia by remember { mutableStateOf(diaNombre(LocalDate.now())) }
    var editando by remember { mutableStateOf<Block?>(null) }
    var nuevo by remember { mutableStateOf(false) }
    val bloques = plan.horario.filter { it.dia == dia }.sortedBy { it.iniMin }

    fun guardar(lista: List<Block>) {
        st.saveHorarioJson(blocksToJson(lista))
        PlanLoader.reload(ctx)
        Notifier.scheduleNext(ctx)
        bump()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Dc.card).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("←", color = Dc.text, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Editar horario", color = Dc.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Toca un bloque para cambiarlo", color = Dc.muted, fontSize = 13.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (d in DIAS) Pill(d, Dc.blurple, dia == d) { dia = d }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(bloques, key = { it.id }) { b ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Dc.card)
                        .clickable { editando = b }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${b.ini}–${b.fin} · ${b.pilar}", color = pilarColor(b.pilar),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                        Text(b.act, color = Dc.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (b.lugar.isNotBlank()) Text("📍 ${b.lugar}", color = Dc.dim, fontSize = 12.sp)
                    }
                    Text(if (b.notif) "🔔" else "🔕")
                }
            }
            item {
                Button(onClick = { nuevo = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Añadir bloque a $dia")
                }
            }
        }
    }

    val ed = editando
    // Se reserva el id una sola vez al abrir "añadir bloque" (y nunca se reutiliza).
    val idNuevo = remember(nuevo) {
        if (nuevo) st.reservarIdBloque(plan.horario.maxOfOrNull { it.id } ?: -1) else -1
    }
    if (ed != null || nuevo) {
        BloqueDialog(
            inicial = ed, diaInicial = dia,
            onSave = { b ->
                val lista = if (ed != null) plan.horario.map { if (it.id == ed.id) b else it }
                else plan.horario + b
                guardar(lista)
                editando = null
                nuevo = false
            },
            siguienteId = idNuevo,
            onDelete = if (ed != null) {
                {
                    guardar(plan.horario.filter { it.id != ed.id })
                    editando = null
                }
            } else null,
            onDismiss = { editando = null; nuevo = false }
        )
    }
}

@Composable
fun BloqueDialog(
    inicial: Block?, diaInicial: String, siguienteId: Int,
    onSave: (Block) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit
) {
    var dia by remember { mutableStateOf(inicial?.dia ?: diaInicial) }
    var ini by remember { mutableStateOf(inicial?.ini?.toString() ?: "19:00") }
    var fin by remember { mutableStateOf(inicial?.fin?.toString() ?: "20:00") }
    var pilar by remember { mutableStateOf(inicial?.pilar ?: "Estudio") }
    var pista by remember { mutableStateOf(if (inicial?.pista == "B") "B" else "A") }
    var act by remember { mutableStateOf(inicial?.act ?: "") }
    var lugar by remember { mutableStateOf(inicial?.lugar ?: "") }
    var notif by remember { mutableStateOf(inicial?.notif ?: true) }
    var err by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (inicial == null) "Nuevo bloque" else "Editar bloque") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Día", color = Dc.muted, fontSize = 12.sp)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (d in DIAS) Pill(d.take(3), Dc.blurple, dia == d) { dia = d }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ini, onValueChange = { ini = it }, singleLine = true,
                        label = { Text("Inicio") }, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fin, onValueChange = { fin = it }, singleLine = true,
                        label = { Text("Fin") }, modifier = Modifier.weight(1f)
                    )
                }
                Text("Tipo", color = Dc.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (p in PILARES) Pill(p, pilarColor(p), pilar == p) { pilar = p }
                }
                if (pilar == "Estudio") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("Pista A", Dc.blurple, pista == "A") { pista = "A" }
                        Pill("Pista B", Color(0xFF8A4FD6), pista == "B") { pista = "B" }
                    }
                }
                OutlinedTextField(
                    value = act, onValueChange = { act = it },
                    label = { Text("Actividad") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = lugar, onValueChange = { lugar = it }, singleLine = true,
                    label = { Text("Dónde (opcional)") }, modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Notificar este bloque", modifier = Modifier.weight(1f))
                    Switch(checked = notif, onCheckedChange = { notif = it })
                }
                if (err.isNotEmpty()) Text(err, color = Dc.red, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val i = parseHm(ini)
                val f = parseHm(fin)
                if (i == null || f == null) {
                    err = "Usa el formato HH:MM, por ejemplo 19:30."
                } else {
                    onSave(
                        Block(
                            inicial?.id ?: siguienteId, dia, i, f, pilar,
                            if (act.isBlank()) pilar else act.trim(), lugar.trim(), notif,
                            if (pilar == "Estudio") pista else ""
                        )
                    )
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Eliminar", color = Dc.red) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

package com.plan.ingsoft

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.createChannels(this)
        Notifier.scheduleNext(this)
        MonitorService.sync(this)
        PlanWidget.updateAll(this)
        SheetsSync.programar(this)
        HorarioSheet.programar(this)
        // Limpieza de ajustes viejos fuera del hilo principal.
        val app = applicationContext
        Thread { Store(app).purgar() }.start()
        setContent {
            PlanTheme { AppRoot() }
        }
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val st = remember { Store(ctx) }
    var tab by remember { mutableStateOf(0) }
    var pantalla by remember { mutableStateOf(0) } // 0 = pestañas, 1 = ajustes, 2 = editor, 3 = progreso, 4 = foco
    var completa by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var pendiente by remember { mutableStateOf<(() -> Unit)?>(null) }
    val leerTick = tick
    val plan = PlanLoader.get(ctx)

    val bump: () -> Unit = {
        tick += 1
        PlanWidget.updateAll(ctx)
        SheetsSync.pedirSincronizacion(ctx)
    }

    // Pide contraseña/retos antes de ejecutar una acción de edición.
    val pedir: (() -> Unit) -> Unit = { accion ->
        if (Lock.isUnlocked()) {
            accion()
        } else {
            pendiente = accion
        }
        Unit
    }

    // Refrescar al volver a la app (por si marcaste algo desde una notificación o el widget).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick += 1
            if (event == Lifecycle.Event.ON_STOP) SheetsSync.pedirSincronizacion(ctx)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val pedirPermiso: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pedirPermiso()
        }
    }

    BackHandler(enabled = pantalla != 0 || completa) {
        if (pantalla != 0) pantalla = if (pantalla == 2) 1 else 0 else completa = false
    }

    // Al abrir la app, trae el horario de la hoja de Google si hay una enlazada.
    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) { HorarioSheet.traerSiToca(ctx) }
        if (r != null && r.cambio) tick += 1
    }

    val tabs = listOf("●" to "Hoy", "▦" to "Horario", "◆" to "Estudio", "▲" to "Entreno", "⋯" to "Más")
    val ocultaBarra = pantalla != 0 || (completa && tab == 1)
    Scaffold(
        containerColor = Dc.bg,
        bottomBar = {
            if (!ocultaBarra) {
                NavigationBar(containerColor = Dc.panel) {
                    tabs.forEachIndexed { i, (icono, nombre) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(icono, fontSize = 18.sp, modifier = Modifier.clearAndSetSemantics { }) },
                            label = { Text(nombre, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Dc.hover,
                                selectedTextColor = Dc.text,
                                unselectedTextColor = Dc.dim
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (pantalla) {
                1 -> AjustesScreen(
                    plan, st, leerTick, bump, pedir, pedirPermiso,
                    onBack = { pantalla = 0 }, onEditor = { pantalla = 2 }
                )
                2 -> HorarioEditorScreen(plan, st, bump, onBack = { pantalla = 1 })
                3 -> ConVolver("Más", onBack = { pantalla = 0 }) { ProgresoScreen(plan, st, leerTick, bump) }
                4 -> ConVolver("Más", onBack = { pantalla = 0 }) { FocoScreen(st, leerTick, bump, pedir) }
                else -> when (tab) {
                    0 -> HoyScreen(plan, st, leerTick, bump, onSettings = { pantalla = 1 })
                    1 -> HorarioScreen(plan, st, leerTick, bump, completa) { completa = it }
                    2 -> EstudioScreen(plan, st, leerTick, bump)
                    3 -> WorkoutScreen(plan, st, leerTick, bump)
                    else -> MasScreen(
                        onProgreso = { pantalla = 3 },
                        onFoco = { pantalla = 4 },
                        onAjustes = { pantalla = 1 }
                    )
                }
            }
        }
    }

    if (pendiente != null) {
        UnlockDialog(
            st = st,
            onDismiss = { pendiente = null },
            onOk = {
                val accion = pendiente
                pendiente = null
                Lock.unlock()
                accion?.invoke()
            }
        )
    }
}

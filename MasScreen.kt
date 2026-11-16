package com.plan.ingsoft

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Menú de lo que no necesitas a diario: progreso, foco y ajustes. */
@Composable
fun MasScreen(onProgreso: () -> Unit, onFoco: () -> Unit, onAjustes: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Más", color = Dc.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        FilaMenu("Progreso", "Estudio y ejercicio", onProgreso)
        FilaMenu("Foco", "Tiempo en pantalla y límites por app", onFoco)
        FilaMenu("Ajustes", "Avisos, hoja de Google, plan y seguridad", onAjustes)
    }
}

@Composable
private fun FilaMenu(titulo: String, detalle: String, onClick: () -> Unit) {
    Panel(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(titulo, color = Dc.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(detalle, color = Dc.muted, fontSize = 13.sp)
            }
            Text("›", color = Dc.dim, fontSize = 22.sp)
        }
    }
}

/** Marco con flecha "‹ Más" para las pantallas que se abren desde el menú. */
@Composable
fun ConVolver(titulo: String, onBack: () -> Unit, contenido: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onBack).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹ $titulo", color = Dc.muted, fontSize = 14.sp)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { contenido() }
    }
}

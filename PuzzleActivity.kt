package com.plan.ingsoft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Se abre desde el bloqueo de una app: resolver los retos da 10 minutos extra. */
class PuzzleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pkg = intent.getStringExtra("pkg") ?: ""
        val label = intent.getStringExtra("label") ?: "la app"
        setContent {
            PlanTheme {
                Surface(Modifier.fillMaxSize(), color = Dc.bg) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        Text(
                            "10 min más en $label", color = Dc.text, fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Resuelve los retos seguidos. Si fallas, se reinicia. Piensa si de verdad lo necesitas.",
                            color = Dc.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp)
                        )
                        PuzzleFlow(listOf(0, 2)) {
                            Store(this@PuzzleActivity).addExtraMin(pkg, 10)
                            finish()
                        }
                        TextButton(onClick = { finish() }) { Text("Mejor no") }
                    }
                }
            }
        }
    }
}

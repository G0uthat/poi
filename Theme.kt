package com.plan.ingsoft

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paleta estilo Discord. Sigue el tema del sistema: oscuro o claro. */
object Dc {
    /** Lo fija [PlanTheme] al empezar a componer. */
    var dark: Boolean = true

    val bg: Color get() = if (dark) Color(0xFF1E1F22) else Color(0xFFF2F3F5)
    val panel: Color get() = if (dark) Color(0xFF2B2D31) else Color(0xFFFFFFFF)
    val card: Color get() = if (dark) Color(0xFF313338) else Color(0xFFFFFFFF)
    val hover: Color get() = if (dark) Color(0xFF404249) else Color(0xFFE3E5E8)
    val blurple = Color(0xFF5865F2)
    val green = Color(0xFF23A55A)
    val red = Color(0xFFF23F42)
    val yellow: Color get() = if (dark) Color(0xFFF0B232) else Color(0xFFD99A00)
    val text: Color get() = if (dark) Color(0xFFF2F3F5) else Color(0xFF060607)
    val muted: Color get() = if (dark) Color(0xFFB5BAC1) else Color(0xFF4E5058)
    val dim: Color get() = if (dark) Color(0xFF80848E) else Color(0xFF6D6F78)
}

fun cap(s: String): String = s.replaceFirstChar { it.uppercase() }

@Composable
fun PlanTheme(content: @Composable () -> Unit) {
    val oscuro = isSystemInDarkTheme()
    Dc.dark = oscuro
    val scheme = if (oscuro) {
        darkColorScheme(
            primary = Dc.blurple,
            onPrimary = Color.White,
            secondary = Dc.green,
            background = Dc.bg,
            onBackground = Dc.text,
            surface = Dc.panel,
            onSurface = Dc.text,
            surfaceVariant = Dc.card,
            onSurfaceVariant = Dc.muted,
            outline = Dc.hover
        )
    } else {
        lightColorScheme(
            primary = Dc.blurple,
            onPrimary = Color.White,
            secondary = Dc.green,
            background = Dc.bg,
            onBackground = Dc.text,
            surface = Dc.panel,
            onSurface = Dc.text,
            surfaceVariant = Dc.card,
            onSurfaceVariant = Dc.muted,
            outline = Dc.hover
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Círculo de "chequeo rápido" (como Any.do / Trello). */
@Composable
fun CheckCircle(done: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (done) color else Color.Transparent)
                .border(2.dp, if (done) color else Dc.dim, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (done) Text("✓", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Pill(text: String, color: Color, selected: Boolean = true, onClick: (() -> Unit)? = null) {
    val base = Modifier
        .clip(RoundedCornerShape(50))
        .background(if (selected) color else Dc.hover)
    val m = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(m.padding(horizontal = 10.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = if (selected) Color.White else Dc.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun Avatar(letter: String, color: Color, dia: Dp = 40.dp) {
    Box(
        Modifier
            .size(dia)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Dc.card)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Dc.dim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp
    )
}

@Composable
fun Bar(frac: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Dc.hover)
    ) {
        Box(
            Modifier
                .fillMaxWidth(frac.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color)
        )
    }
}

@Composable
fun Ring(frac: Float, label: String, dia: Dp = 56.dp) {
    Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 5.dp.toPx()
            val trazo = Stroke(width = w, cap = StrokeCap.Round)
            val tam = Size(this.size.width - w, this.size.height - w)
            drawArc(
                color = Dc.hover, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(w / 2, w / 2), size = tam, style = trazo
            )
            if (frac > 0f) {
                drawArc(
                    color = Dc.green, startAngle = -90f,
                    sweepAngle = 360f * frac.coerceIn(0f, 1f), useCenter = false,
                    topLeft = Offset(w / 2, w / 2), size = tam, style = trazo
                )
            }
        }
        Text(label, color = Dc.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

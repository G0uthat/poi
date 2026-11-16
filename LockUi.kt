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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.Dialog

/**
 * Serie de retos (0 = cuentas mentales, 1 = apaga las luces, 2 = torres de Hanói).
 * Hay que resolverlos todos seguidos para continuar.
 */
@Composable
fun PuzzleFlow(tipos: List<Int>, onDone: () -> Unit) {
    var paso by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Reto ${paso + 1} de ${tipos.size}", color = Dc.muted, fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val avanzar: () -> Unit = {
            if (paso + 1 >= tipos.size) onDone() else paso += 1
        }
        when (tipos[paso]) {
            0 -> MathStage(avanzar)
            1 -> LightsStage(avanzar)
            else -> HanoiStage(avanzar)
        }
    }
}

@Composable
fun MathStage(onOk: () -> Unit) {
    var seed by remember { mutableStateOf(0) }
    val set = remember(seed) { Puzzles.mathSet() }
    var qi by remember(seed) { mutableStateOf(0) }
    var txt by remember(seed) { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    Column {
        Text("Cuentas mentales ${qi + 1}/${set.size}", color = Dc.text, fontWeight = FontWeight.SemiBold)
        Text(
            set[qi].text + " = ?", color = Dc.text, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 10.dp)
        )
        OutlinedTextField(
            value = txt, onValueChange = { txt = it.filter { c -> c.isDigit() || c == '-' } },
            singleLine = true, label = { Text("Tu respuesta") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (err) {
            Text("Incorrecto. Se reinicia la serie con otras cuentas.", color = Dc.red, fontSize = 12.sp)
        }
        Button(
            onClick = {
                val v = txt.trim().toLongOrNull()
                if (v != null && v == set[qi].answer) {
                    err = false
                    txt = ""
                    if (qi + 1 >= set.size) onOk() else qi += 1
                } else {
                    err = true
                    seed += 1
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) { Text("Comprobar") }
    }
}

@Composable
fun LightsStage(onOk: () -> Unit) {
    var seed by remember { mutableStateOf(0) }
    val game = remember(seed) { Puzzles.Lights() }
    var rev by remember(seed) { mutableStateOf(0) }
    var movs by remember(seed) { mutableStateOf(0) }
    val leer = rev
    Column {
        Text("Apaga todas las luces", color = Dc.text, fontWeight = FontWeight.SemiBold)
        Text(
            "Al tocar una luz se invierten ella y sus vecinas. Movimientos: $movs",
            color = Dc.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)
        )
        for (r in 0 until game.n) {
            Row {
                for (c in 0 until game.n) {
                    val i = r * game.n + c
                    val encendida = game.cells[i]
                    Box(
                        Modifier
                            .padding(3.dp)
                            .size(58.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (encendida && leer >= 0) Dc.yellow else Dc.hover)
                            .clickable {
                                game.press(i)
                                movs += 1
                                rev += 1
                                if (game.solved()) onOk()
                            }
                    )
                }
            }
        }
        OutlinedButton(onClick = { seed += 1 }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Nuevo tablero")
        }
    }
}

@Composable
fun HanoiStage(onOk: () -> Unit) {
    var seed by remember { mutableStateOf(0) }
    val game = remember(seed) { Puzzles.Hanoi(4) }
    var rev by remember(seed) { mutableStateOf(0) }
    var sel by remember(seed) { mutableStateOf(-1) }
    val leer = rev
    val colores = listOf(Dc.blurple, Dc.green, Dc.yellow, Dc.red)
    Column {
        Text("Torres de Hanói", color = Dc.text, fontWeight = FontWeight.SemiBold)
        Text(
            "Pasa todos los discos al tercer poste. Un disco grande nunca va sobre uno pequeño. " +
                "Toca el poste de origen y luego el de destino. Movimientos: ${game.moves}",
            color = Dc.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in 0 until 3) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel == p) Dc.hover else Dc.card)
                        .clickable {
                            if (sel == -1) {
                                if (game.pegs[p].isNotEmpty()) sel = p
                            } else {
                                game.move(sel, p)
                                sel = -1
                                rev += 1
                                if (game.solved()) onOk()
                            }
                        }
                        .padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (leer >= 0) {
                        for (d in game.pegs[p].reversed()) {
                            Box(
                                Modifier
                                    .padding(vertical = 2.dp)
                                    .width((26 + d * 20).dp)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colores[(d - 1) % colores.size])
                            )
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { seed += 1 }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Reiniciar")
        }
    }
}

/** Ventana para desbloquear la edición: contraseña o retos. */
@Composable
fun UnlockDialog(st: Store, onDismiss: () -> Unit, onOk: () -> Unit) {
    val tiene = Lock.hasPassword(st)
    var modo by remember { mutableStateOf(if (tiene) 0 else 1) }
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    var espera by remember { mutableStateOf(Lock.segundosEspera(st)) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Dc.panel) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("🔒 Edición bloqueada", color = Dc.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Cambiar tu plan, horarios o límites requiere contraseña o resolver retos.",
                    color = Dc.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tiene) Pill("Contraseña", Dc.blurple, modo == 0) { modo = 0 }
                    Pill("Retos", Dc.blurple, modo == 1) { modo = 1 }
                }
                Spacer(Modifier.height(12.dp))
                if (modo == 0) {
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it; err = false },
                        singleLine = true, label = { Text("Contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (espera > 0) {
                        Text(
                            "Demasiados intentos. Espera unos $espera s o usa los retos.",
                            color = Dc.yellow, fontSize = 12.sp
                        )
                    } else if (err) {
                        Text("Contraseña incorrecta", color = Dc.red, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            if (Lock.check(st, pass)) {
                                onOk()
                            } else {
                                err = true
                                espera = Lock.segundosEspera(st)
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Desbloquear") }
                } else {
                    PuzzleFlow(listOf(0, 1, 2), onOk)
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    }
}

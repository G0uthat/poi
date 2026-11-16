package com.plan.ingsoft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Progreso falso en memoria: la lógica del plan se prueba sin Android. */
private class FakeProgreso : Progreso {
    val hitos = HashMap<String, Long>()
    val bloques = HashSet<Pair<LocalDate, Int>>()
    override fun isHitoDone(id: String) = hitos.containsKey(id)
    override fun hitoFecha(id: String) = hitos[id] ?: 0L
    override fun isBlockDone(date: LocalDate, id: Int) = (date to id) in bloques
}

class PlanTest {

    // 2026-11-16 es lunes.
    private val lunes = LocalDate.of(2026, 11, 16)

    private fun bloque(id: Int, dia: String, ini: String, fin: String, pilar: String = "Estudio", pista: String = "A") =
        Block(id, dia, LocalTime.parse(ini), LocalTime.parse(fin), pilar, "Actividad $id", "", true, pista)

    private fun plan(
        inicio: LocalDate = lunes,
        horario: List<Block> = emptyList(),
        hitos: List<Hito> = listOf(
            Hito("A1", "Curso A", "Primero", "", 10.0),
            Hito("A2", "Curso A", "Segundo", "", 10.0)
        ),
        eficiencia: Double = 0.8
    ) = Plan(
        inicio, eficiencia, horario,
        listOf(Curso("Curso A", "A", "", "", "")),
        hitos, emptyList()
    )

    @Test
    fun bloqueQueCruzaLaMedianocheSeDetectaAlDiaSiguiente() {
        val p = plan(horario = listOf(bloque(1, "Lunes", "23:00", "01:00")))
        assertEquals(2.0, p.horario[0].horas, 0.001)
        assertNotNull(p.bloqueActual(LocalDateTime.of(2026, 11, 16, 23, 30)))
        assertNotNull(p.bloqueActual(LocalDateTime.of(2026, 11, 17, 0, 30)))
        assertNull(p.bloqueActual(LocalDateTime.of(2026, 11, 17, 1, 30)))
    }

    @Test
    fun siguienteHitoSaltaLosHechos() {
        val p = plan()
        val s = FakeProgreso()
        assertEquals("A1", p.siguienteHito(s, "A")?.id)
        s.hitos["A1"] = lunes.toEpochDay()
        assertEquals("A2", p.siguienteHito(s, "A")?.id)
        s.hitos["A2"] = lunes.toEpochDay()
        assertNull(p.siguienteHito(s, "A"))
        assertNull(p.siguienteHito(s, "B"))
    }

    @Test
    fun rachaCuentaDiasSeguidosCumplidos() {
        val dias = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        val horario = dias.mapIndexed { i, d -> bloque(i, d, "19:00", "20:00") }
        val p = plan(inicio = lunes.minusDays(10), horario = horario)
        val s = FakeProgreso()
        // Domingo 15, sábado 14 y lunes 16 cumplidos; el viernes 13 no.
        s.bloques.add(lunes to 0)
        s.bloques.add(lunes.minusDays(1) to 6)
        s.bloques.add(lunes.minusDays(2) to 5)
        assertEquals(3, p.racha(s, lunes))
    }

    @Test
    fun rachaNoSeRompeSiHoyTodaviaNoSeMarco() {
        val dias = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        val p = plan(inicio = lunes.minusDays(10), horario = dias.mapIndexed { i, d -> bloque(i, d, "19:00", "20:00") })
        val s = FakeProgreso()
        s.bloques.add(lunes.minusDays(1) to 6)
        assertEquals(1, p.racha(s, lunes))
    }

    @Test
    fun elPlanTerminaALas12Semanas() {
        val p = plan()
        assertFalse(p.terminado(lunes.plusDays(83)))
        assertTrue(p.terminado(lunes.plusDays(84)))
        assertEquals(12, p.semanaActual(lunes.plusDays(200)))
        assertEquals(1, p.semanaActual(lunes))
    }

    @Test
    fun estadoGlobalComparaHorasHechasConLasEsperadas() {
        // 10 h de estudio por semana x 0,8 = 8 h efectivas por semana.
        val horario = listOf(bloque(1, "Lunes", "08:00", "18:00"))
        val p = plan(horario = horario)
        val s = FakeProgreso()
        val unaSemana = lunes.plusDays(7)
        assertTrue(p.estadoGlobal(s, unaSemana).startsWith("▼"))
        s.hitos["A1"] = lunes.toEpochDay() // 10 h hechas (≥ 8 esperadas)
        assertTrue(p.estadoGlobal(s, unaSemana).startsWith("▲"))
    }

    @Test
    fun diaConBloquesMarcadosAlMenosAl80PorCientoCuenta() {
        val horario = (0 until 5).map { bloque(it, "Lunes", "0${it + 1}:00", "0${it + 1}:30") }
        val p = plan(horario = horario)
        val s = FakeProgreso()
        for (i in 0 until 3) s.bloques.add(lunes to i)
        assertFalse(p.cumplido(s, lunes))
        s.bloques.add(lunes to 3)
        assertTrue(p.cumplido(s, lunes))
    }

    @Test
    fun textoBloqueMuestraElBloqueAnteriorTrasLaMedianoche() {
        val p = plan(horario = listOf(bloque(1, "Lunes", "23:00", "01:00")))
        val t = p.textoBloque(LocalDateTime.of(2026, 11, 17, 0, 30))
        assertTrue(t.startsWith("Ahora"))
    }
}

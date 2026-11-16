package com.plan.ingsoft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HorarioSheetTest {

    @Test
    fun idSeSacaDelEnlaceOSueltoYSeRechazaLoDemas() {
        val id = "1fkPV2Uha9aCbO85BUU_IMq1nt73mik4ucU29XJRmGAk"
        assertEquals(id, HorarioSheet.idDe("https://docs.google.com/spreadsheets/d/$id/edit?usp=sharing"))
        assertEquals(id, HorarioSheet.idDe("  $id "))
        assertNull(HorarioSheet.idDe("https://www.google.com"))
        assertNull(HorarioSheet.idDe(""))
    }

    @Test
    fun csvEntiendeComillasComasYSaltosDentroDeCeldas() {
        val f = HorarioSheet.parseCsv("\"Día\",\"Actividad\"\r\n\"Lunes\",\"Leer, \"\"bien\"\"\nsegunda línea\"\n\"Martes\",\"\"\n")
        assertEquals(3, f.size)
        assertEquals(listOf("Día", "Actividad"), f[0])
        assertEquals("Leer, \"bien\"\nsegunda línea", f[1][1])
        assertEquals(listOf("Martes", ""), f[2])
        assertEquals(2, HorarioSheet.parseCsv("a,b\nc,d").size) // sin salto final
    }

    @Test
    fun horasEnTodosLosFormatosQueDevuelveSheets() {
        assertEquals(LocalTime.of(19, 0), HorarioSheet.parseHora("19:00"))
        assertEquals(LocalTime.of(19, 0), HorarioSheet.parseHora("19:00:00"))
        assertEquals(LocalTime.of(7, 5), HorarioSheet.parseHora("7:05"))
        assertEquals(LocalTime.of(19, 30), HorarioSheet.parseHora("7:30:00 PM"))
        assertEquals(LocalTime.of(19, 30), HorarioSheet.parseHora("7:30:00 p. m."))
        assertEquals(LocalTime.of(0, 15), HorarioSheet.parseHora("12:15 AM"))
        assertEquals(LocalTime.of(12, 0), HorarioSheet.parseHora("12:00 PM"))
        assertEquals(LocalTime.of(19, 0), HorarioSheet.parseHora("0,7916666667"))
        assertEquals(LocalTime.MIDNIGHT, HorarioSheet.parseHora("24:00"))
        assertNull(HorarioSheet.parseHora("25:00"))
        assertNull(HorarioSheet.parseHora("abc"))
        assertNull(HorarioSheet.parseHora(""))
    }

    @Test
    fun diasConTildesAbreviadosOEnMayusculas() {
        assertEquals("Miércoles", HorarioSheet.parseDia("miércoles"))
        assertEquals("Miércoles", HorarioSheet.parseDia("MIERCOLES "))
        assertEquals("Sábado", HorarioSheet.parseDia("Sáb"))
        assertEquals("Lunes", HorarioSheet.parseDia("lun."))
        assertNull(HorarioSheet.parseDia("Mañana"))
    }

    private val cab = listOf("Día", "Inicio", "Fin", "Pilar", "Actividad", "Lugar", "Notificar")

    @Test
    fun filasValidasSeConviertenEnBloquesYLasMalasSeCuentan() {
        val lectura = HorarioSheet.bloquesDeFilas(
            listOf(
                cab,
                listOf("Lunes", "19:00", "21:00", "Estudio", "Hito profundo", "Escritorio", ""),
                listOf("Lunes", "21:30", "22:00", "Entreno", "Calistenia", "", "No"),
                listOf("Domingo", "10:00", "12:30", "Estudio", "Segundo curso", "", ""),
                listOf("Martes", "hora rara", "10:00", "Estudio", "Mal", "", ""),
                listOf("", "", "", "", "", "", "")
            )
        )
        assertNotNull(lectura)
        val b = lectura!!.bloques
        assertEquals(3, b.size)
        assertEquals(1, lectura.ignoradas)
        assertEquals("Estudio", b[0].pilar)
        assertEquals("A", b[0].pista)
        assertEquals("Escritorio", b[0].lugar)
        assertEquals("Entrenamiento", b[1].pilar)
        assertFalse(b[1].notif)
        assertTrue(b[0].notif)
        assertEquals("B", b[2].pista)
        assertEquals(LocalTime.of(10, 0), b[2].ini)
    }

    @Test
    fun laCabeceraPuedeEstarEnOtroOrdenYConOtrosNombres() {
        val lectura = HorarioSheet.bloquesDeFilas(
            listOf(
                listOf("Tarea", "Hasta", "Desde", "Dónde", "Día"),
                listOf("Correr", "7:30 AM", "6:30 AM", "Parque", "viernes")
            )
        )
        val b = lectura!!.bloques[0]
        assertEquals("Viernes", b.dia)
        assertEquals(LocalTime.of(6, 30), b.ini)
        assertEquals(LocalTime.of(7, 30), b.fin)
        assertEquals("Parque", b.lugar)
        assertEquals("Personal", b.pilar)
    }

    @Test
    fun sinColumnasObligatoriasNoSeLeeNada() {
        assertNull(HorarioSheet.bloquesDeFilas(listOf(listOf("A", "B"), listOf("1", "2"))))
        assertNull(HorarioSheet.bloquesDeFilas(emptyList()))
    }

    @Test
    fun elIdNoCambiaSiSoloCambiaLaHoraYNoSeRepiteEntreBloquesIguales() {
        fun lee(ini: String) = HorarioSheet.bloquesDeFilas(
            listOf(
                cab,
                listOf("Lunes", ini, "21:00", "Estudio", "Estudio A", "", ""),
                listOf("Lunes", "22:00", "23:00", "Estudio", "Estudio A", "", "")
            )
        )!!.bloques
        val a = lee("19:00")
        val b = lee("18:00")
        assertEquals(a[0].id, b[0].id)
        assertEquals(a[1].id, b[1].id)
        assertTrue(a[0].id != a[1].id)
        assertTrue(a[0].id >= 1_000_000)
    }

    @Test
    fun ajustesDeLaPestanaPlan() {
        val aj = HorarioSheet.ajustesDeFilas(
            listOf(listOf("Clave", "Valor"), listOf("Inicio", "16/11/2026"), listOf("Eficiencia", "85%"))
        )
        assertEquals(LocalDate.of(2026, 11, 16), aj.inicio)
        assertEquals(0.85, aj.eficiencia!!, 0.0001)
        val vacio = HorarioSheet.ajustesDeFilas(listOf(listOf("Inicio", "no sé"), listOf("Eficiencia", "5")))
        assertNull(vacio.inicio)
        assertNull(vacio.eficiencia)
        assertEquals(LocalDate.of(2026, 1, 5), HorarioSheet.parseFecha("2026-01-05"))
    }
}

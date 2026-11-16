package com.plan.ingsoft

import kotlin.random.Random

class MathProblem(val text: String, val answer: Long)

/** Puzzles de "fricción": hay que esforzarse un poco para cambiar ajustes o pedir más tiempo. */
object Puzzles {

    /** 5 cuentas mentales de dificultad media. */
    fun mathSet(rnd: Random = Random.Default, n: Int = 5): List<MathProblem> {
        val out = ArrayList<MathProblem>()
        val kinds = (0 until 5).toMutableList()
        kinds.shuffle(rnd)
        for (i in 0 until n) {
            out.add(
                when (kinds[i % kinds.size]) {
                    0 -> {
                        val a = rnd.nextInt(12, 40)
                        val b = rnd.nextInt(11, 30)
                        MathProblem("$a × $b", (a * b).toLong())
                    }
                    1 -> {
                        val a = rnd.nextInt(20, 80)
                        val b = rnd.nextInt(20, 80)
                        val c = rnd.nextInt(3, 10)
                        MathProblem("($a + $b) × $c", ((a + b) * c).toLong())
                    }
                    2 -> {
                        val a = rnd.nextInt(13, 26)
                        val b = rnd.nextInt(10, 100)
                        MathProblem("$a × $a − $b", (a * a - b).toLong())
                    }
                    3 -> {
                        val a = rnd.nextInt(100, 1000)
                        val b = rnd.nextInt(100, 1000)
                        val c = rnd.nextInt(100, 1000)
                        val d = rnd.nextInt(100, 1000)
                        MathProblem("$a + $b + $c + $d", (a + b + c + d).toLong())
                    }
                    else -> {
                        val a = rnd.nextInt(21, 100)
                        val b = rnd.nextInt(3, 10)
                        val c = rnd.nextInt(21, 100)
                        val d = rnd.nextInt(3, 10)
                        MathProblem("$a × $b − $c × $d", (a * b - c * d).toLong())
                    }
                }
            )
        }
        return out
    }

    /** Apaga todas las luces. Siempre tiene solución (se genera desde el tablero apagado). */
    class Lights(val n: Int = 4, rnd: Random = Random.Default) {
        val cells = BooleanArray(n * n)
        val solution: List<Int>

        init {
            val idx = (0 until n * n).toMutableList()
            var sol: List<Int>
            do {
                java.util.Arrays.fill(cells, false)
                idx.shuffle(rnd)
                sol = idx.take(rnd.nextInt(8, 11))
                for (i in sol) press(i)
            } while (solved())
            solution = sol
        }

        fun press(i: Int) {
            val r = i / n
            val c = i % n
            toggle(r, c)
            toggle(r - 1, c)
            toggle(r + 1, c)
            toggle(r, c - 1)
            toggle(r, c + 1)
        }

        private fun toggle(r: Int, c: Int) {
            if (r in 0 until n && c in 0 until n) cells[r * n + c] = !cells[r * n + c]
        }

        fun solved(): Boolean = cells.none { it }
    }

    /** Torres de Hanói: mover todos los discos al tercer poste (mínimo 2^n − 1 movimientos). */
    class Hanoi(val discs: Int = 4) {
        val pegs: Array<MutableList<Int>> = Array(3) { ArrayList<Int>() }
        var moves = 0

        init {
            for (d in discs downTo 1) pegs[0].add(d)
        }

        fun move(from: Int, to: Int): Boolean {
            if (from == to || pegs[from].isEmpty()) return false
            val d = pegs[from].last()
            if (pegs[to].isNotEmpty() && pegs[to].last() < d) return false
            pegs[from].removeAt(pegs[from].size - 1)
            pegs[to].add(d)
            moves++
            return true
        }

        fun solved(): Boolean = pegs[2].size == discs
    }
}

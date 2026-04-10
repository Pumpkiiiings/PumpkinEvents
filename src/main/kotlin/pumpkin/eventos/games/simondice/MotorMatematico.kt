package pumpkin.eventos.games.simondice

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object MotorMatematico {

    fun evaluar(str: String): Double {
        return try {
            val cleanStr = str.lowercase().replace(" ", "")
            if (cleanStr.contains("x") && cleanStr.contains("=")) {
                resolverEcuacion(cleanStr)
            } else {
                Parser(cleanStr).parse()
            }
        } catch (e: Exception) {
            -999999.0
        }
    }

    private class Parser(private val str: String) {
        private var pos = -1
        private var ch = -1

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw RuntimeException("Inesperado: ${ch.toChar()}")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> x /= parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else if (ch in 'a'.code..'z'.code) {
                while (ch in 'a'.code..'z'.code) nextChar()
                val func = str.substring(startPos, pos)
                x = parseFactor()
                x = when (func) {
                    "sqrt" -> sqrt(x)
                    "sin" -> sin(Math.toRadians(x))
                    "cos" -> cos(Math.toRadians(x))
                    "tan" -> tan(Math.toRadians(x))
                    else -> throw RuntimeException("Función desconocida")
                }
            } else {
                throw RuntimeException("Inesperado")
            }

            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }

    private fun resolverEcuacion(ecuacion: String): Double {
        return try {
            val partes = ecuacion.split("=")
            val ladoX = if (partes[0].contains("x")) partes[0] else partes[1]
            val ladoNum = if (partes[0].contains("x")) partes[1] else partes[0]

            val objetivo = Parser(ladoNum).parse()

            var x = -500.0
            while (x <= 1000.0) {
                val temporal = ladoX.replace("x", "($x)")
                if (abs(Parser(temporal).parse() - objetivo) < 0.1) {
                    return Math.round(x * 100.0) / 100.0
                }
                x += 0.1
            }
            -999999.0
        } catch (e: Exception) {
            -999999.0
        }
    }
}
package pumpkin.eventos.games.ruletarusa

import kotlin.random.Random

data class TriggerResult(
    val fired: Boolean,
    val riskBeforeShot: Double,
    val remainingChambersBeforeShot: Int
)

/** Modelo de dominio del cilindro; no depende de Bukkit y puede probarse aislado. */
class RouletteCylinder(
    private val chamberCount: Int = 6,
    private val bulletSelector: (Int) -> Int = { Random.nextInt(it) }
) {
    init {
        require(chamberCount >= 2) { "A cylinder needs at least two chambers" }
    }

    private var bulletPosition = 0
    private var currentPosition = 0

    val remainingChambers: Int get() = chamberCount - currentPosition
    val currentRisk: Double get() = 1.0 / remainingChambers
    val currentChamberLoaded: Boolean get() = currentPosition == bulletPosition

    init {
        reload()
    }

    fun reload() {
        bulletPosition = bulletSelector(chamberCount).coerceIn(0, chamberCount - 1)
        currentPosition = 0
    }

    /** Pasar conserva la cámara actual: el riesgo se transfiere al siguiente jugador. */
    fun pass() = Unit

    fun pullTrigger(): TriggerResult {
        val result = TriggerResult(currentChamberLoaded, currentRisk, remainingChambers)
        if (result.fired || currentPosition == chamberCount - 1) reload() else currentPosition++
        return result
    }
}

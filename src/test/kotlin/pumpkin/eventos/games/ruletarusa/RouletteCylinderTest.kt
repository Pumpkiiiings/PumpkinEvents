package pumpkin.eventos.games.ruletarusa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouletteCylinderTest {

    @Test
    fun `risk grows after every empty chamber`() {
        val cylinder = RouletteCylinder(bulletSelector = { 5 })

        assertEquals(1.0 / 6.0, cylinder.currentRisk)
        assertFalse(cylinder.pullTrigger().fired)
        assertEquals(1.0 / 5.0, cylinder.currentRisk)
        assertFalse(cylinder.pullTrigger().fired)
        assertEquals(1.0 / 4.0, cylinder.currentRisk)
    }

    @Test
    fun `passing preserves chamber and risk`() {
        val cylinder = RouletteCylinder(bulletSelector = { 2 })
        val risk = cylinder.currentRisk

        cylinder.pass()

        assertEquals(risk, cylinder.currentRisk)
        assertFalse(cylinder.currentChamberLoaded)
    }

    @Test
    fun `loaded chamber fires and reloads cylinder`() {
        var reloads = 0
        val cylinder = RouletteCylinder(bulletSelector = { reloads++; 0 })

        val result = cylinder.pullTrigger()

        assertTrue(result.fired)
        assertEquals(1.0 / 6.0, cylinder.currentRisk)
        assertEquals(2, reloads)
    }
}

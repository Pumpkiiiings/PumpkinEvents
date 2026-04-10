package pumpkin.eventos.utils

import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.World

object WorldUtils {

    /**
     * Configura las reglas necesarias para los eventos en un mundo.
     */
    fun setupWorldRules(world: World) {
        // ACTIVAR INSTA RESPAWN
        if (world.getGameRuleValue(GameRule.DO_IMMEDIATE_RESPAWN) != true) {
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
        }

        // REGLAS EXTRA RECOMENDADAS PARA EVENTOS (Opcional)
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false) // No spam de logros
        world.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false) // Ahorro de RAM
    }

    /**
     * Aplica el Insta Respawn a absolutamente todos los mundos cargados.
     */
    fun applyToAllWorlds() {
        Bukkit.getWorlds().forEach { setupWorldRules(it) }
    }
}

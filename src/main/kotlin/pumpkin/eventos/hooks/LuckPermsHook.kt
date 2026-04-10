package pumpkin.eventos.hooks

import net.luckperms.api.LuckPermsProvider
import org.bukkit.entity.Player

/**
 * [PUMPKIN EVENTOS CORE]
 * Hook directo a LuckPerms para obtener prefijos, sufijos y pesos (weights)
 * Súper optimizado usando el caché interno de la API.
 */
class LuckPermsHook {
    private val lp = LuckPermsProvider.get()

    /**
     * Obtiene el prefijo del jugador (soporta herencia y prioridades de LP).
     */
    fun getPrefix(player: Player): String {
        val user = lp.userManager.getUser(player.uniqueId) ?: return ""
        return user.cachedData.metaData.prefix ?: ""
    }

    /**
     * Obtiene el sufijo del jugador.
     */
    fun getSuffix(player: Player): String {
        val user = lp.userManager.getUser(player.uniqueId) ?: return ""
        return user.cachedData.metaData.suffix ?: ""
    }

    /**
     * Obtiene el nombre del grupo principal.
     */
    fun getPrimaryGroup(player: Player): String {
        val user = lp.userManager.getUser(player.uniqueId)
        return user?.primaryGroup ?: "default"
    }

    /**
     * Obtiene el peso (prioridad) del grupo principal del jugador.
     * Es vital para ordenar el Tablist (Admins arriba, Default abajo).
     */
    fun getWeight(player: Player): Int {
        val user = lp.userManager.getUser(player.uniqueId) ?: return 0

        // Obtenemos el objeto del grupo principal
        val group = lp.groupManager.getGroup(user.primaryGroup) ?: return 0

        // Retornamos el peso si existe, si no, 0
        return if (group.weight.isPresent) group.weight.asInt else 0
    }
}

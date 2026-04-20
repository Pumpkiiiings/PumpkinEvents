package pumpkin.eventos.games.miniwalls

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.entity.Wither
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import pumpkin.eventos.PumpkinEventos

class MiniWallsListener(private val plugin: PumpkinEventos, private val game: MiniWalls) : Listener {

    // --- PROTECCIÓN DE CONSTRUCCIÓN ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(e: BlockPlaceEvent) {
        if (!game.isRunning || !game.players.contains(e.player)) return

        // Limite de altura
        if (e.block.y > game.maxHeightLimit) {
            e.isCancelled = true
            e.player.sendMessage(plugin.messageManager.parse("<red>¡Has alcanzado el límite de altura!</red>"))
            return
        }

        // Registrar el bloque para que pueda ser roto después
        game.placedBlocks.add(e.block.location)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(e: BlockBreakEvent) {
        if (!game.isRunning || !game.players.contains(e.player)) return

        // Fase de Preparación o Lobby: Cero roturas
        if (game.phase == MwPhase.PREPARING || game.phase == MwPhase.LOBBY) {
            e.isCancelled = true
            return
        }

        // Si no está en la lista de bloques puestos por jugadores, no se puede romper
        if (!game.placedBlocks.contains(e.block.location)) {
            e.isCancelled = true
            e.player.sendMessage(plugin.messageManager.parse("<red>Solo puedes romper bloques puestos por jugadores.</red>"))
            return
        }

        game.placedBlocks.remove(e.block.location)
    }

    // Evitar que el Wither rompa el mapa
    @EventHandler
    fun onWitherBreak(e: EntityChangeBlockEvent) {
        if (!game.isRunning) return
        if (e.entity is Wither) e.isCancelled = true
    }

    // --- SELECCIÓN DE EQUIPO EN EL LOBBY ---
    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        if (!game.isRunning || game.phase != MwPhase.LOBBY) return

        if (e.item?.type == Material.WHITE_WOOL) {
            e.isCancelled = true
            // Cambiar de equipo secuencialmente
            val teams = MwTeam.values()
            val current = game.playerTeam[p]
            val nextIndex = if (current == null) 0 else (teams.indexOf(current) + 1) % teams.size
            val nextTeam = teams[nextIndex]

            game.playerTeam[p] = nextTeam
            p.sendMessage(plugin.messageManager.parse("<white>Seleccionaste el ${nextTeam.chatColor}<b>${nextTeam.displayName}</b></white>"))
            p.playSound(p.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
        }
    }

    // --- DAÑO AL WITHER ---
    @EventHandler
    fun onWitherDamage(e: EntityDamageByEntityEvent) {
        if (!game.isRunning) return
        val wither = e.entity as? Wither ?: return

        var attacker = e.damager as? Player
        if (attacker == null && e.damager is Arrow) {
            attacker = (e.damager as Arrow).shooter as? Player
        }
        if (attacker == null) return

        val witherTeam = game.teamWithers.entries.find { it.value == wither }?.key ?: return
        val attackerTeam = game.playerTeam[attacker]

        if (witherTeam == attackerTeam) {
            e.isCancelled = true // No puedes pegar a tu propio nexo
            attacker.sendMessage(plugin.messageManager.parse("<red>¡No puedes dañar a tu propio Nexo!</red>"))
            return
        }

        // Avisar al equipo que su Wither está bajo ataque
        val hp = (wither.health - e.finalDamage).toInt().coerceAtLeast(0)
        game.players.filter { game.playerTeam[it] == witherTeam }.forEach {
            it.sendActionBar(plugin.messageManager.parse("<red>⚠️ <b>¡TU WITHER ESTÁ BAJO ATAQUE! ($hp HP)</b></red>"))
            it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f)
        }
    }

    @EventHandler
    fun onWitherDeath(e: EntityDeathEvent) {
        if (!game.isRunning) return
        val wither = e.entity as? Wither ?: return
        val team = game.teamWithers.entries.find { it.value == wither }?.key ?: return

        plugin.server.broadcast(plugin.messageManager.parse("<newline>☠ ${team.chatColor}<b>¡EL WITHER ${team.displayName.uppercase()} HA SIDO DESTRUIDO!</b></${team.chatColor.replace("<#", "<")}> <white>¡Ya no reaparecerán!</white><newline>"))
        game.players.forEach { it.playSound(it.location, Sound.ENTITY_WITHER_DEATH, 1f, 1f) }
    }

    // --- CURACIÓN POR KILL (Kits) ---
    @EventHandler
    fun onPlayerKill(e: PlayerDeathEvent) {
        if (!game.isRunning) return
        val killer = e.entity.killer ?: return
        if (!game.players.contains(killer)) return

        val kit = game.playerKit[killer] ?: return
        if (kit.healOnKill > 0.0) {
            killer.health = (killer.health + kit.healOnKill).coerceAtMost(killer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0)
            killer.playSound(killer.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
            killer.sendMessage(plugin.messageManager.parse("<#FF1493>❤ <b>+${kit.healOnKill / 2} Corazones</b></#FF1493>"))
        }
    }
}

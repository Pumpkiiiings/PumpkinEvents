package pumpkin.eventos.games.lava

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import pumpkin.eventos.PumpkinEventos

class SueloLavaListener(private val plugin: PumpkinEventos) : Listener {

    // Helper para obtener la instancia del juego si es que está corriendo
    private fun game(): SueloLava? = plugin.eventManager.currentGame as? SueloLava

    // --- CONSTRUCCIÓN ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(e: BlockPlaceEvent) {
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(e.player)) return

        // Prohibir construcción durante la preparación (cuenta regresiva)
        if (game.phase == SlPhase.PREPARING) {
            e.isCancelled = true
        } else {
            // Permitir construcción en fase PLAYING / DEATHMATCH
            e.isCancelled = false
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(e: BlockBreakEvent) {
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(e.player)) return

        // Prohibir romper bloques durante la preparación
        if (game.phase == SlPhase.PREPARING) {
            e.isCancelled = true
        } else {
            // Permitir romper bloques y que DROPEEN el ítem como Minecraft Normal
            e.isCancelled = false
            e.isDropItems = true
        }
    }

    // --- COMBATE ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPvp(e: EntityDamageByEntityEvent) {
        val attacker = e.damager as? Player ?: return
        val victim = e.entity as? Player ?: return
        val game = game() ?: return

        if (!game.isRunning) return
        if (!game.players.contains(attacker) || !game.players.contains(victim)) {
            e.isCancelled = true
            return
        }

        // Bloqueo de daño en fase de preparación
        if (game.phase == SlPhase.PREPARING) {
            e.isCancelled = true
            return
        }

        // El PvP depende de si fue habilitado por la votación (o modo caos)
        if (!game.pvpEnabled) {
            e.isCancelled = true
        }
    }

    // --- DAÑO AMBIENTAL ---
    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(e: EntityDamageEvent) {
        val player = e.entity as? Player ?: return
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(player)) return

        // El daño entre entidades ya se manejó arriba en onPvp
        if (e is EntityDamageByEntityEvent) return

        // Invulnerables mientras preparan
        if (game.phase == SlPhase.PREPARING) {
            e.isCancelled = true
            return
        }

        // Muerte automática e instatánea si tocan el elemento mortal
        if (e.cause == EntityDamageEvent.DamageCause.LAVA ||
            e.cause == EntityDamageEvent.DamageCause.FIRE ||
            e.cause == EntityDamageEvent.DamageCause.FIRE_TICK) {

            e.isCancelled = true
            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                game.eliminate(player)
            }
            return
        }

        // Evitar que mueran bugueados en paredes
        if (e.cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
            e.cause == EntityDamageEvent.DamageCause.FALLING_BLOCK) {
            e.isCancelled = true
            return
        }
    }

    // --- INTERACCIÓN Y COFRES INTELIGENTES ---
    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(p)) return

        // No pueden abrir cofres o comer durante los 15 segundos iniciales
        if (game.phase == SlPhase.PREPARING) {
            e.isCancelled = true
            return
        }

        // Lógica de llenado de Cofres/Barriles
        if (e.action == Action.RIGHT_CLICK_BLOCK) {
            val block = e.clickedBlock ?: return

            // Cubre Cofres, Cofres Trampa y Barriles
            if (block.state is Container) {
                if (!game.openedChests.contains(block.location)) {
                    game.openedChests.add(block.location)

                    // casteamos a org.bukkit.block.Chest porque tu función fillChestWithLoot
                    // requiere ese parámetro (Asegúrate de que tus mapas usen cofres reales y no barriles)
                    val state = block.state as? org.bukkit.block.Chest

                    if (state != null) {
                        game.fillChestWithLoot(state)
                        p.playSound(p.location, Sound.BLOCK_CHEST_OPEN, 1f, 1f)
                    }
                }
            }
        }
    }
}

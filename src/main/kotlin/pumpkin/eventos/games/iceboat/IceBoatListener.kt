package pumpkin.eventos.games.iceboat

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Boat
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.vehicle.VehicleDamageEvent
import org.bukkit.event.vehicle.VehicleExitEvent
import org.bukkit.event.vehicle.VehicleMoveEvent
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent
import org.bukkit.util.Vector
import pumpkin.eventos.PumpkinEventos

import com.destroystokyo.paper.event.player.PlayerJumpEvent
import java.time.Duration

class IceBoatListener(private val plugin: PumpkinEventos) : Listener {

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (game.phase != RacePhase.VOTING || !game.isRunning) return

        if (e.item?.type == Material.OAK_BOAT) {
            e.isCancelled = true
            game.abrirMenuVotos(e.player)
        }
    }

    @EventHandler
    fun onDismount(e: VehicleExitEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        val p = e.exited as? Player ?: return

        // Si el juego corre y el jugador no ha ganado aún, no se puede bajar
        if (game.isRunning && game.players.contains(p) && !game.ganadores.contains(p.uniqueId)) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onVehicleDamage(e: VehicleDamageEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (game.isRunning && e.vehicle is Boat) e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(e: EntityDamageEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (game.isRunning) e.isCancelled = true
    }

    @EventHandler
    fun onVehicleMove(e: VehicleMoveEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (!game.isRunning) return

        val boat = e.vehicle as? Boat ?: return
        if (game.phase == RacePhase.PREPARING) {
            boat.velocity = Vector(0.0, 0.0, 0.0)
            return
        }
    }

    @EventHandler
    fun onJump(e: PlayerJumpEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (game.phase != RacePhase.RACING || !game.isRunning) return

        val p = e.player
        if (!game.players.contains(p) || game.ganadores.contains(p.uniqueId)) return

        val boat = p.vehicle as? Boat ?: return

        val blockBelow = boat.location.clone().subtract(0.0, 0.1, 0.0).block
        if (blockBelow.type.isSolid || blockBelow.type == Material.ICE || blockBelow.type == Material.PACKED_ICE || blockBelow.type == Material.BLUE_ICE) {
            val currentVel = boat.velocity
            boat.velocity = Vector(currentVel.x, 0.45, currentVel.z)
            p.playSound(p.location, Sound.ENTITY_RABBIT_JUMP, 1f, 1f)
        }
    }

    @EventHandler
    fun onPlayerMove(e: PlayerMoveEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (game.phase != RacePhase.RACING) return

        val p = e.player
        val uuid = p.uniqueId
        if (!game.players.contains(p) || game.ganadores.contains(uuid)) return

        val loc = p.location
        val arena = game.currentArena ?: return

        // 1. CHECKPOINTS
        val checks = arena.chairs
        val currentCpIndex = game.playerCheckpoints[uuid] ?: 0

        if (checks.isNotEmpty() && currentCpIndex < checks.size) {
            val targetCp = checks[currentCpIndex]

            if (loc.world == game.gameWorld && loc.distance(targetWorldLoc(game, targetCp)) < 15.0) {
                game.playerCheckpoints[uuid] = currentCpIndex + 1
                p.playSound(p.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f)

                // Si es el PRIMER CHECKPOINT de la vuelta, iniciamos el cronómetro
                if (currentCpIndex == 0) {
                    game.lapStartTimes[uuid] = System.currentTimeMillis()
                }
                // En vez de Action Bar (porque choca con el HUD de velocidad), enviamos un subtítulo
                p.showTitle(Title.title(
                    net.kyori.adventure.text.Component.empty(),
                    plugin.languageManager.component(
                        "common.titles.iceboat_checkpoint.subtitle",
                        Placeholder.unparsed("checkpoint", (currentCpIndex + 1).toString()),
                        Placeholder.unparsed("total", checks.size.toString())
                    ),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(250))
                ))
            }
        }

        // 2. LÍNEA DE META
        if (loc.blockX in game.minX..game.maxX && loc.blockZ in game.minZ..game.maxZ) {
            if (checks.isEmpty() || currentCpIndex >= checks.size) {

                // --- CÁLCULO DE TIEMPO DE VUELTA ---
                val startTime = game.lapStartTimes[uuid]
                if (startTime != null) {
                    val lapTimeMs = System.currentTimeMillis() - startTime
                    val prevBest = game.bestLapTimes[uuid] ?: Long.MAX_VALUE

                    if (lapTimeMs < prevBest) {
                        game.bestLapTimes[uuid] = lapTimeMs // Récord personal
                        p.sendMessage(plugin.messageManager.parse("<#39FF14>⏱ <b>¡NUEVO RÉCORD DE VUELTA!</b> <white>${game.formatMs(lapTimeMs)}</white></#39FF14>"))
                    } else {
                        p.sendMessage(plugin.messageManager.parse("<gray>⏱ Vuelta completada en: ${game.formatMs(lapTimeMs)}</gray>"))
                    }
                }

                // Reiniciamos variables
                val vueltasActuales = (game.playerLaps[uuid] ?: 0) + 1
                game.playerLaps[uuid] = vueltasActuales
                game.playerCheckpoints[uuid] = 0
                game.lapStartTimes.remove(uuid) // Se reiniciará al tocar el primer CP

                if (vueltasActuales >= game.totalLaps) {
                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        p.vehicle?.remove()
                        game.markWinner(p)
                    }
                } else {
                    p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                    p.showTitle(Title.title(
                        plugin.languageManager.component(
                            "common.titles.iceboat_lap.main",
                            Placeholder.unparsed("lap", vueltasActuales.toString())
                        ),
                        plugin.languageManager.component("common.titles.iceboat_lap.subtitle"),
                        Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1500), Duration.ofMillis(250))
                    ))
                }
            }
        }
    }

    private fun targetWorldLoc(game: IceBoatRacing, loc: Location): Location {
        return Location(game.gameWorld, loc.x, loc.y, loc.z)
    }

    @EventHandler
    fun onBlockCollision(e: VehicleBlockCollisionEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (!game.isRunning || game.phase != RacePhase.RACING) return

        val boat = e.vehicle as? Boat ?: return
        val p = boat.passengers.firstOrNull() as? Player ?: return
        if (!game.players.contains(p) || game.ganadores.contains(p.uniqueId)) return

        // Partículas de chispas
        boat.world.spawnParticle(org.bukkit.Particle.LAVA, boat.location, 3, 0.5, 0.5, 0.5, 0.05)
        boat.world.spawnParticle(org.bukkit.Particle.CRIT, boat.location, 5, 0.5, 0.5, 0.5, 0.1)
    }

    @EventHandler
    fun onEntityCollision(e: VehicleEntityCollisionEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (!game.isRunning || game.phase != RacePhase.RACING) return

        val boat = e.vehicle as? Boat ?: return
        val p = boat.passengers.firstOrNull() as? Player ?: return
        if (!game.players.contains(p) || game.ganadores.contains(p.uniqueId)) return

        // Partículas de chispas
        boat.world.spawnParticle(org.bukkit.Particle.LAVA, boat.location, 3, 0.5, 0.5, 0.5, 0.05)
        boat.world.spawnParticle(org.bukkit.Particle.CRIT, boat.location, 5, 0.5, 0.5, 0.5, 0.1)
    }
}

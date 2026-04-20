package pumpkin.eventos.games.iceboat

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
import org.bukkit.util.Vector
import pumpkin.eventos.PumpkinEventos

class IceBoatListener(private val plugin: PumpkinEventos) : Listener {

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    // --- VOTACIONES ---
    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (game.phase != RacePhase.VOTING || !game.isRunning) return

        if (e.item?.type == Material.OAK_BOAT) {
            e.isCancelled = true
            game.abrirMenuVotos(e.player)
        }
    }

    // --- ANTI TRAMPAS Y BLOQUEOS DE BOTE ---
    @EventHandler
    fun onDismount(e: VehicleExitEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (game.isRunning && e.exited is Player && game.players.contains(e.exited as Player)) {
            e.isCancelled = true // No pueden bajarse del bote jamás
        }
    }

    @EventHandler
    fun onVehicleDamage(e: VehicleDamageEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (game.isRunning && e.vehicle is Boat) {
            e.isCancelled = true // Los botes son indestructibles
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(e: EntityDamageEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (game.isRunning) e.isCancelled = true // Jugadores inmortales en la carrera
    }

    // --- CONGELAR ANTES DE SALIR Y AUTO-STEP ---
    @EventHandler
    fun onVehicleMove(e: VehicleMoveEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (!game.isRunning) return

        val boat = e.vehicle as? Boat ?: return
        val player = boat.passengers.firstOrNull() as? Player ?: return

        // Congelar botes antes de arrancar
        if (game.phase == RacePhase.PREPARING) {
            boat.velocity = Vector(0.0, 0.0, 0.0)
            return
        }

        if (game.phase == RacePhase.RACING) {
            // --- AUTO-STEP (Subir 1 bloque de hielo sin chocar) ---
            val from = e.from
            val to = e.to

            // Si el bote intenta avanzar pero se choca contra un bloque enfrente
            val frontBlock = boat.world.getBlockAt(to.clone().add(boat.location.direction.multiply(1.5)))
            val aboveBlock = frontBlock.getRelative(org.bukkit.block.BlockFace.UP)

            // Si hay un bloque enfrente, pero hay espacio arriba para subir
            if (frontBlock.type.isSolid && aboveBlock.type.isAir) {
                // Pequeño salto automático
                boat.velocity = boat.velocity.setY(0.4)
            }
        }
    }

    // --- CHECKPOINTS Y LÍNEA DE META ---
    @EventHandler
    fun onPlayerMove(e: PlayerMoveEvent) {
        val game = plugin.eventManager.currentGame as? IceBoatRacing ?: return
        if (game.phase != RacePhase.RACING) return

        val p = e.player
        if (!game.players.contains(p)) return

        val loc = p.location
        val uuid = p.uniqueId
        val arena = game.currentArena ?: return

        // 1. CHECKPOINTS (Las sillas de la arena actúan como coordenadas de Checkpoints invisibles)
        val checks = arena.chairs
        val currentCpIndex = game.playerCheckpoints[uuid] ?: 0

        if (checks.isNotEmpty() && currentCpIndex < checks.size) {
            val targetCp = checks[currentCpIndex]

            // Si pasa a menos de 10 bloques (Es un radio de tolerancia amplio)
            if (loc.world == game.gameWorld && loc.distance(targetWorldLoc(game, targetCp)) < 15.0) {
                game.playerCheckpoints[uuid] = currentCpIndex + 1
                p.playSound(p.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f)
                p.sendActionBar(plugin.messageManager.parse("<green>✔ Checkpoint ${currentCpIndex + 1}/${checks.size}</green>"))
            }
        }

        // 2. LÍNEA DE META
        if (loc.blockX in game.minX..game.maxX && loc.blockZ in game.minZ..game.maxZ) {
            // Solo cruza la meta si ya pasó por todos los checkpoints (Evita que den vueltas en la meta para ganar)
            if (checks.isEmpty() || currentCpIndex >= checks.size) {

                val vueltasActuales = (game.playerLaps[uuid] ?: 0) + 1
                game.playerLaps[uuid] = vueltasActuales
                game.playerCheckpoints[uuid] = 0 // Reiniciar checkpoints para la siguiente vuelta

                if (vueltasActuales >= game.totalLaps) {
                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        p.vehicle?.remove()
                        game.markWinner(p)
                    }
                } else {
                    p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                    p.sendTitle("§b§lVUELTA $vueltasActuales", "§f¡Sigue corriendo!", 5, 30, 5)
                }
            }
        }
    }

    private fun targetWorldLoc(game: IceBoatRacing, loc: Location): Location {
        return Location(game.gameWorld, loc.x, loc.y, loc.z)
    }
}

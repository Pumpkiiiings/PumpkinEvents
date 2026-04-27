package pumpkin.eventos.games

import org.bukkit.Sound
import org.bukkit.World
import pumpkin.eventos.PumpkinEventos
import java.util.concurrent.TimeUnit

/**
 * Maneja el WorldBorder shrink progresivo para modos de juego.
 *
 * Comportamiento:
 * - Centro: arena.centerLocation (coordenadas X/Z del world border)
 * - Tamaño inicial: 100x100 (diámetro 100, radio 50)
 * - Cada [intervalSeconds] segundos se contrae [shrinkBlocks] bloques por lado (diámetro -shrinkBlocks*2)
 * - Sonido BELL cuando empieza a moverse, ANVIL cuando termina el movimiento
 */
class BorderShrinkManager(
    private val plugin: PumpkinEventos,
    private val game: EventGame
) {
    private var currentSize = 100.0
    private val minSize = 10.0
    private val shrinkBlocks = 5.0   // bloques por LADO (diámetro total -10 cada ciclo)
    private var intervalSeconds = 30L
    private val transitionSeconds = 10L // duración de la animación del border moviéndose
    private var isActive = false

    fun start(world: World, centerX: Double, centerZ: Double, delaySeconds: Long = 0L, intervalSecs: Long = 30L) {
        this.intervalSeconds = intervalSecs
        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            val border = world.worldBorder
            border.setCenter(centerX, centerZ)
            border.size = 100.0
            border.warningDistance = 5
            border.warningTime = 5
        }
        currentSize = 100.0
        isActive = true

        plugin.server.asyncScheduler.runDelayed(plugin, { _ ->
            scheduleNextShrink(world, centerX, centerZ)
        }, delaySeconds, TimeUnit.SECONDS)

        // Damage task (1 corazón por segundo fuera del borde)
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!game.isRunning || !isActive) { task.cancel(); return@runAtFixedRate }
            
            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                val border = world.worldBorder
                game.players.forEach { p ->
                    if (p.world == world && !border.isInside(p.location)) {
                        val damage = 2.0 // 1 corazón
                        p.removePotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION)
                        val newHp = (p.health - damage).coerceAtLeast(0.0)
                        p.health = newHp
                        p.playSound(p.location, Sound.ENTITY_PLAYER_HURT, 1f, 0.8f)
                        p.sendActionBar(plugin.messageManager.parse("<red><b>⚠ ¡ESTÁS FUERA DEL BORDE!</b></red>"))
                        if (newHp <= 0.0) {
                            game.eliminate(p)
                        }
                    }
                }
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun scheduleNextShrink(world: World, cx: Double, cz: Double) {
        if (!game.isRunning) return

        plugin.server.asyncScheduler.runDelayed(plugin, { _ ->
            if (!game.isRunning) return@runDelayed

            val newSize = (currentSize - shrinkBlocks * 2).coerceAtLeast(minSize)
            if (newSize >= currentSize) return@runDelayed

            // Sonido de campana = border empieza a moverse
            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                game.players.forEach { p ->
                    p.playSound(p.location, Sound.BLOCK_BELL_USE, 1f, 1f)
                }
            }

            // Animar el movimiento del border
            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                world.worldBorder.setSize(newSize, transitionSeconds)
            }
            currentSize = newSize

            // Cuando termina la animación → sonido de yunque
            plugin.server.asyncScheduler.runDelayed(plugin, { _ ->
                if (!game.isRunning) return@runDelayed
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    game.players.forEach { p ->
                        p.playSound(p.location, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.2f)
                    }
                }

                if (currentSize > minSize) {
                    scheduleNextShrink(world, cx, cz)
                }
            }, transitionSeconds, TimeUnit.SECONDS)

        }, intervalSeconds, TimeUnit.SECONDS)
    }

    fun stop(world: World) {
        isActive = false
        val border = world.worldBorder
        border.reset()
    }
}

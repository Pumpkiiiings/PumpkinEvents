package pumpkin.eventos.games.battleroyale

import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.concurrent.TimeUnit

// Fases del borde al estilo Fortnite
data class BorderPhase(val sizeBlocks: Double, val durationSeconds: Long, val damagePerSecond: Double, val label: String)

class BattleRoyale(plugin: PumpkinEventos) : EventGame(plugin, "battleroyale", "<#FF3131>Battle Royale</#FF3131>") {

    var mainTimer = 600        // 10 minutos máximo
    var isPreparation = true
    var prepTimer = 10
    val openedChests = mutableSetOf<Location>()
    val kills = mutableMapOf<Player, Int>()

    // Loot drops OP activos (location → cleanup task reference)
    val activeDrops = mutableSetOf<Location>()

    // Seguimiento de refills
    private var refill1Done = false
    private var refill2Done = false

    // Borde integrado — fases progresivas
    // (tamaño en bloques, duración de esa fase en segundos, daño/s fuera, etiqueta)
    private val borderPhases = listOf(
        BorderPhase(200.0,  180L, 1.0,  "Zona Segura"),   // Fase 1: a los 3 min → borde 200
        BorderPhase(100.0,  180L, 2.0,  "Zona Peligrosa"),// Fase 2: a los 6 min → borde 100
        BorderPhase(40.0,   120L, 4.0,  "Zona Crítica"),  // Fase 3: a los 9 min → borde 40
        BorderPhase(10.0,    60L, 8.0,  "Zona Final")     // Fase 4: último minuto
    )
    private var currentPhase = -1
    var borderDamage = 0.0   // Daño actual por segundo fuera del borde

    private val cages = mutableListOf<Location>()

    override fun getCustomGameRules(): Map<GameRule<*>, Any> =
        mapOf(GameRule.FALL_DAMAGE to true, GameRule.DO_MOB_SPAWNING to false, GameRule.KEEP_INVENTORY to false)

    override fun getExtraPlaceholders(): Map<String, String> {
        val min = mainTimer / 60; val sec = mainTimer % 60
        val phaseLabel = if (currentPhase in borderPhases.indices) borderPhases[currentPhase].label else "Preparando..."
        
        val killsList = kills.entries.sortedByDescending { it.value }
        val k1 = killsList.getOrNull(0)?.let { "${it.key.name} - ${it.value}" } ?: "Nadie - 0"
        val k2 = killsList.getOrNull(1)?.let { "${it.key.name} - ${it.value}" } ?: "Nadie - 0"
        val k3 = killsList.getOrNull(2)?.let { "${it.key.name} - ${it.value}" } ?: "Nadie - 0"
        
        return mapOf(
            "%br_vivos%" to players.size.toString(),
            "%br_timer%" to String.format("%02d:%02d", min, sec),
            "%br_fase%" to phaseLabel,
            "%br_kills_1%" to k1,
            "%br_kills_2%" to k2,
            "%br_kills_3%" to k3
        )
    }

    override fun onStart() {
        mainTimer = 600; isPreparation = true; prepTimer = 10
        openedChests.clear(); kills.clear(); activeDrops.clear(); cages.clear()
        refill1Done = false; refill2Done = false; currentPhase = -1; borderDamage = 0.0

        val arena = currentArena ?: return
        val targetWorld = gameWorld ?: return

        // Inicializar borde en tamaño grande
        val border = targetWorld.worldBorder
        border.center = arena.centerLocation ?: targetWorld.spawnLocation
        border.size = 500.0
        border.damageBuffer = 0.0
        border.damageAmount = 0.0  // El daño lo gestionamos manualmente

        // Spawnar jugadores en jaulas
        val spawns = arena.spawnPoints
        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            players.forEachIndexed { idx, p ->
                val loc = spawns.getOrNull(idx % spawns.size.coerceAtLeast(1))?.clone()?.apply { world = targetWorld } ?: return@forEachIndexed
                p.teleportAsync(loc).thenAccept {
                    p.gameMode = GameMode.SURVIVAL
                    p.inventory.clear()
                    // Kit inicial: casco+botas de cuero + herramientas de madera
                    val armor = BattleRoyaleLoot.kitArmaduraInicial()
                    p.inventory.helmet = armor["helmet"]
                    p.inventory.boots = armor["boots"]
                    val tools = BattleRoyaleLoot.kitInicial()
                    tools.forEachIndexed { i, item -> p.inventory.setItem(i, item) }
                    plugin.server.regionScheduler.runDelayed(plugin, loc, { _ -> crearJaula(loc) }, 3L)
                }
                kills[p] = 0
            }
        }, 10L)

        // Timer principal
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            if (isPreparation) {
                prepTimer--
                players.forEach { it.sendActionBar(plugin.messageManager.parse("<yellow>La batalla comienza en: <b>${prepTimer}s</b></yellow>")) }
                if (prepTimer <= 0) {
                    isPreparation = false
                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        cages.forEach { it.block.setType(Material.AIR, false) }
                        cages.clear()
                        players.forEach { p ->
                            p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                            p.showTitle(Title.title(
                                plugin.messageManager.parse("<#FF3131><b>¡BATTLE ROYALE!</b></#FF3131>"),
                                plugin.messageManager.parse("<white>¡Sobrevive y sé el último en pie!")
                            ))
                        }
                        plugin.server.broadcast(plugin.messageManager.parse("\n<#FF3131><b>⚔ BATTLE ROYALE HA COMENZADO</b></#FF3131> <white>» ¡El último en pie gana!\n"))
                    }
                }
                return@runAtFixedRate
            }

            mainTimer--

            // Comprobar condición de victoria
            if (players.size <= 1) {
                task.cancel()
                plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                return@runAtFixedRate
            }

            // REFILL 1 — minuto 5 (300s restantes)
            if (mainTimer == 300 && !refill1Done) {
                refill1Done = true
                openedChests.clear()
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    plugin.server.broadcast(plugin.messageManager.parse("\n<#39FF14><b>♻ REFILL #1</b></#39FF14> <white>» ¡Los cofres han sido rellenados!\n"))
                    players.forEach { it.playSound(it.location, Sound.BLOCK_CHEST_OPEN, 1f, 0.5f) }
                }
            }

            // REFILL 2 — minuto 8 (120s restantes)
            if (mainTimer == 120 && !refill2Done) {
                refill2Done = true
                openedChests.clear()
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    plugin.server.broadcast(plugin.messageManager.parse("\n<#FFCC00><b>♻ REFILL #2 (FINAL)</b></#FFCC00> <white>» ¡Última oportunidad de lootearse!\n"))
                    players.forEach { it.playSound(it.location, Sound.BLOCK_CHEST_OPEN, 1f, 0.8f) }
                }
            }

            // BORDE — fases en minutos 3, 6, 9, 10
            val phaseTimestamps = listOf(420, 240, 60, 0)  // segundos restantes para cada fase
            phaseTimestamps.forEachIndexed { idx, timestamp ->
                if (mainTimer == timestamp && currentPhase < idx) {
                    currentPhase = idx
                    val phase = borderPhases[idx]
                    borderDamage = phase.damagePerSecond
                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        val center = currentArena?.centerLocation ?: return@run
                        val world = gameWorld ?: return@run
                        val border = world.worldBorder
                        border.center = center
                        border.setSize(phase.sizeBlocks, phase.durationSeconds)
                        plugin.server.broadcast(plugin.messageManager.parse(
                            "\n<#FF3131><b>⚠ LA ZONA SEGURA SE ESTÁ CERRANDO</b></#FF3131> <white>» ${phase.label} — ${phase.sizeBlocks.toInt()} bloques de radio.\n"
                        ))
                        players.forEach { p ->
                            p.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 1f, 1f)
                            p.showTitle(Title.title(
                                plugin.messageManager.parse("<#FF3131><b>⚠ ZONA REDUCIÉNDOSE</b></#FF3131>"),
                                plugin.messageManager.parse("<white>${phase.label} — ¡Métete al círculo!</white>")
                            ))
                        }
                    }
                }
            }

            // Daño fuera del borde (verificación en scheduler global)
            if (borderDamage > 0) {
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    val world = gameWorld ?: return@run
                    val border = world.worldBorder
                    players.forEach { p ->
                        if (!border.isInside(p.location)) {
                            p.damage(borderDamage)
                            p.sendActionBar(plugin.messageManager.parse("<#FF3131><b>⚠ ¡Estás fuera de la zona segura! (-${borderDamage.toInt()} ❤/s)</b></#FF3131>"))
                        }
                    }
                }
            }

            // LOOT DROP OP — cada 3 minutos (180s)
            if (mainTimer % 180 == 0 && mainTimer < 540) {
                plugin.server.globalRegionScheduler.run(plugin) { _ -> lanzarLootDrop() }
            }

            if (mainTimer <= 0) {
                task.cancel()
                plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun crearJaula(loc: Location) {
        val cx = loc.blockX; val cy = loc.blockY; val cz = loc.blockZ
        val w = loc.world ?: return
        plugin.server.regionScheduler.run(plugin, loc) { _ ->
            for (x in -1..1) for (y in -1..3) for (z in -1..1) {
                if (x == -1 || x == 1 || z == -1 || z == 1 || y == -1 || y == 3) {
                    val b = w.getBlockAt(cx + x, cy + y, cz + z)
                    if (b.type.isAir) { b.setType(Material.GLASS, false); cages.add(b.location) }
                }
            }
        }
    }

    private fun lanzarLootDrop() {
        val arena = currentArena ?: return
        val world = gameWorld ?: return
        val center = arena.centerLocation ?: return

        // Posición aleatoria dentro del mapa (radio aleatorio hasta 150 bloques del centro)
        val angle = Math.random() * 2 * Math.PI
        val radius = (20..100).random().toDouble()
        val x = center.x + radius * Math.cos(angle)
        val z = center.z + radius * Math.sin(angle)

        // Encontrar el bloque sólido más alto en esa posición
        val targetY = world.getHighestBlockYAt(x.toInt(), z.toInt()) + 1.0
        val dropLoc = Location(world, x + 0.5, targetY, z + 0.5)

        plugin.server.regionScheduler.run(plugin, dropLoc) { _ ->
            // Colocar cofre
            val block = dropLoc.block
            block.type = Material.CHEST
            val chestState = block.state as? org.bukkit.block.Chest ?: return@run
            chestState.inventory.clear()
            val loot = BattleRoyaleLoot.generarLootOP()
            loot.forEach { item -> chestState.inventory.setItem((0 until 27).random(), item) }

            activeDrops.add(dropLoc)

            // Partículas de impacto (TOTEM)
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, dropLoc.clone().add(0.5, 1.0, 0.5), 60, 1.0, 1.0, 1.0, 0.1)

            // Firework de anuncio
            val fw = world.spawn(dropLoc.clone().add(0.5, 1.0, 0.5), Firework::class.java)
            val fwMeta = fw.fireworkMeta
            fwMeta.addEffect(FireworkEffect.builder()
                .withColor(Color.RED, Color.YELLOW)
                .with(FireworkEffect.Type.BURST)
                .withTrail().withFlicker().build())
            fwMeta.power = 1
            fw.fireworkMeta = fwMeta

            plugin.server.broadcast(plugin.messageManager.parse(
                "\n<#FFD700><b>📦 LOOT DROP OP</b></#FFD700> <white>» ¡Un cofre especial ha caído en el mapa! Coordenadas: <yellow>${x.toInt()}, ${targetY.toInt()}, ${z.toInt()}</yellow>\n"
            ))
            players.forEach { it.playSound(it.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.7f) }

            // Loop de partículas FLAME mientras exista el cofre (30 segundos)
            var countdown = 30
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { particleTask ->
                if (!isRunning || countdown <= 0 || !activeDrops.contains(dropLoc)) {
                    particleTask.cancel()
                    plugin.server.regionScheduler.run(plugin, dropLoc) { _ ->
                        if (block.type == Material.CHEST) {
                            block.type = Material.AIR
                            activeDrops.remove(dropLoc)
                        }
                    }
                    return@runAtFixedRate
                }
                world.spawnParticle(Particle.FLAME, dropLoc.clone().add(0.5, 1.5, 0.5), 8, 0.3, 0.3, 0.3, 0.02)
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, dropLoc.clone().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.05)
                countdown--
            }, 1L, 20L)
        }
    }

    fun registerKill(killer: Player) {
        kills[killer] = (kills[killer] ?: 0) + 1
    }

    override fun checkWinner() {
        val winner = players.firstOrNull()
        val rawMsg = if (winner != null) {
            "<newline><#FF3131><b>BATTLE ROYALE</b></#FF3131> <white>» ¡<yellow>${winner.name}</yellow> ha ganado la partida con <green>${kills[winner] ?: 0} kills</green>!<newline>"
        } else {
            "<newline><#FF3131><b>BATTLE ROYALE</b></#FF3131> <white>» ¡Empate! No queda nadie.<newline>"
        }
        plugin.server.broadcast(plugin.messageManager.parse(rawMsg))
        winner?.let {
            it.playSound(it.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
            it.world.spawnParticle(Particle.FIREWORK, it.location, 100, 1.0, 1.0, 1.0)
            plugin.puntajeManager.addPoints(it, 15, "¡Victoria en Battle Royale!")
        }
        stop()
    }

    override fun onStop() {
        plugin.eventManager.currentGame = null
        // Restaurar borde del mundo
        gameWorld?.let { w ->
            val border = w.worldBorder
            border.size = 29999984.0
            border.damageAmount = 0.0
        }
        val lobby = plugin.arenaManager.mainLobby ?: plugin.server.worlds[0].spawnLocation
        (players + spectators).forEach { p ->
            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}

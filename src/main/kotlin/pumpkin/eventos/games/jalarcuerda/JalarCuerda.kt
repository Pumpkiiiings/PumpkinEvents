package pumpkin.eventos.games.jalarcuerda

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerAnimationType
import org.bukkit.event.player.PlayerInteractEvent
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.time.Duration
import java.util.concurrent.TimeUnit

// Clase para guardar el progreso y la velocidad de cada jugador
class PlayerRhythmData {
    var score: Int = 0
    var cursorPos: Int = 0
    var movingRight: Boolean = true
    var speedDelay: Int = 6 // Ticks entre cada movimiento (Más bajo = Más rápido)
    var ticksSinceLastMove: Int = 0
    var canClick: Boolean = true
}

enum class EquipoCuerda(val nombre: String, val color: String) {
    ROJO("Equipo Rojo", "<#FF3131>"),
    AZUL("Equipo Azul", "<#00FFFF>")
}

class JalarCuerda(plugin: PumpkinEventos) : EventGame(plugin, "jalarcuerda", "<#FF9900>Jalar la Cuerda</#FF9900>"), Listener {

    var isPreparation = true
    private var prepTimer = 10
    var gameTimer = 60 // 1 Minuto de duración de partida

    private val playerData = mutableMapOf<Player, PlayerRhythmData>()
    private val playerTeam = mutableMapOf<Player, EquipoCuerda>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun getExtraPlaceholders(): Map<String, String> {
        val ptsRojo = players.filter { playerTeam[it] == EquipoCuerda.ROJO }.sumOf { playerData[it]?.score ?: 0 }
        val ptsAzul = players.filter { playerTeam[it] == EquipoCuerda.AZUL }.sumOf { playerData[it]?.score ?: 0 }

        return mapOf(
            "%time_left%" to gameTimer.toString(),
            "%pts_rojo%" to ptsRojo.toString(),
            "%pts_azul%" to ptsAzul.toString()
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return mapOf(
            GameRule.FALL_DAMAGE to false,
            GameRule.DO_MOB_SPAWNING to false,
            GameRule.NATURAL_REGENERATION to false
        )
    }

    override fun onStart() {
        isPreparation = true
        prepTimer = 10
        gameTimer = 60
        playerData.clear()
        playerTeam.clear()

        val arena = currentArena ?: return

        // --- RETRASO PARA RE-UBICAR A LOS EQUIPOS ---
        // Esperamos 10 ticks para que el EventManager los termine de llevar al centro
        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->

            // Tomamos el mundo actual donde ya fueron teletransportados
            val targetWorld = players.firstOrNull()?.world ?: arena.centerLocation?.world ?: return@runDelayed

            val posRojo = arena.dueloA ?: arena.spawnPoints.getOrNull(0) ?: return@runDelayed
            val posAzul = arena.dueloB ?: arena.spawnPoints.getOrNull(1) ?: return@runDelayed

            posRojo.world = targetWorld
            posAzul.world = targetWorld

            // --- DIVISIÓN EQUITATIVA DE EQUIPOS ---
            val shuffledPlayers = players.shuffled()
            for (i in shuffledPlayers.indices) {
                val p = shuffledPlayers[i]

                // MODO SURVIVAL OBLIGATORIO: Permite interactuar con los bloques (Golpear al aire)
                p.gameMode = GameMode.SURVIVAL
                p.inventory.clear()
                playerData[p] = PlayerRhythmData()

                if (i % 2 == 0) {
                    playerTeam[p] = EquipoCuerda.ROJO
                    p.teleportAsync(posRojo)
                    p.sendMessage(plugin.messageManager.parse("<white>¡Has sido asignado al <#FF3131><b>Equipo Rojo</b></#FF3131>!</white>"))
                } else {
                    playerTeam[p] = EquipoCuerda.AZUL
                    p.teleportAsync(posAzul)
                    p.sendMessage(plugin.messageManager.parse("<white>¡Has sido asignado al <#00FFFF><b>Equipo Azul</b></#00FFFF>!</white>"))
                }
            }

            // --- RELOJ PRINCIPAL (1 SEGUNDO) ---
            plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
                if (!isRunning) { task.cancel(); return@runAtFixedRate }

                if (isPreparation) {
                    if (prepTimer > 0) {
                        val rawMsg = plugin.languageManager.get("jalarcuerda.actionbar.preparation")
                        val bar = plugin.messageManager.parse(rawMsg ?: "<yellow>El duelo inicia en: <bold>$prepTimer</bold>s</yellow>")
                        players.forEach {
                            it.sendActionBar(bar)
                            it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f)
                        }
                        prepTimer--
                    } else {
                        isPreparation = false
                        val startMsg = plugin.messageManager.parse("<#39FF14><b>¡JALEN LA CUERDA!</b></#39FF14>")
                        players.forEach {
                            it.sendActionBar(startMsg)
                            it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                        }
                        iniciarMotorRitmo() // Inicia el motor visual rápido
                    }
                    return@runAtFixedRate
                }

                gameTimer--
                if (gameTimer <= 0) {
                    task.cancel()
                    plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                }

            }, 1, 1, TimeUnit.SECONDS)

        }, 10L)
    }

    // --- MOTOR DE RITMO (CADA 50ms = 1 TICK APROX) ---
    private fun iniciarMotorRitmo() {
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || isPreparation) { task.cancel(); return@runAtFixedRate }

            players.forEach { p ->
                val data = playerData[p] ?: return@forEach

                data.ticksSinceLastMove++
                if (data.ticksSinceLastMove >= data.speedDelay) {
                    // Mover Cursor de un lado a otro
                    if (data.movingRight) {
                        data.cursorPos++
                        if (data.cursorPos >= 4) data.movingRight = false
                    } else {
                        data.cursorPos--
                        if (data.cursorPos <= 0) data.movingRight = true
                    }

                    data.ticksSinceLastMove = 0
                    data.canClick = true // Recargamos la capacidad de clickear para este cuadro

                    enviarTituloRitmo(p, data.cursorPos)
                }
            }
        }, 1L, 50L, TimeUnit.MILLISECONDS)
    }

    private fun enviarTituloRitmo(p: Player, pos: Int) {
        val cursorRaw = buildString {
            for (i in 0..4) {
                if (i == pos) append("<white>▼</white>")
                else append("<gray>•</gray>")
                if (i < 4) append("  ")
            }
        }
        // Diseño de la barra: ROJO - AMARILLO - VERDE - AMARILLO - ROJO
        val barRaw = "<red>█</red>  <yellow>█</yellow>  <green>█</green>  <yellow>█</yellow>  <red>█</red>"

        // Tiempos en 0 para que la animación sea fluida y no parpadee
        val times = Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ZERO)
        val title = Title.title(plugin.messageManager.parse(cursorRaw), plugin.messageManager.parse(barRaw), times)
        p.showTitle(title)
    }

    // --- DETECTAR CLICS EN LA CUERDA ---
    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        if (plugin.eventManager.currentGame != this || !isRunning || isPreparation) return

        if (e.action == Action.LEFT_CLICK_BLOCK || e.action == Action.RIGHT_CLICK_BLOCK ||
            e.action == Action.LEFT_CLICK_AIR || e.action == Action.RIGHT_CLICK_AIR) {

            // Verificamos el bloque que el jugador está mirando (hasta a 6 bloques de distancia)
            val block = p.getTargetBlockExact(6) ?: return
            val blockName = block.type.name

            if (blockName.contains("CHAIN") || blockName.contains("HAY")) {
                registrarJalon(p)
            }
        }
    }

    // Backup de seguridad para jugadores de Bedrock (Geyser) que a veces fallan el Interact Event
    @EventHandler
    fun onSwing(e: PlayerAnimationEvent) {
        val p = e.player
        if (plugin.eventManager.currentGame != this || !isRunning || isPreparation) return

        if (e.animationType == PlayerAnimationType.ARM_SWING) {
            val block = p.getTargetBlockExact(6) ?: return
            val blockName = block.type.name

            if (blockName.contains("CHAIN") || blockName.contains("HAY")) {
                registrarJalon(p)
            }
        }
    }

    private fun registrarJalon(p: Player) {
        val data = playerData[p] ?: return

        if (!data.canClick) return // Evita Auto-Clickers: 1 clic válido por cada movimiento del cursor
        data.canClick = false

        when (data.cursorPos) {
            2 -> { // VERDE (Centro - Perfecto)
                data.score += 3
                data.speedDelay = maxOf(2, data.speedDelay - 1) // Más rápido (Sube la dificultad)
                p.playSound(p.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f)
                p.sendActionBar(plugin.messageManager.parse("<green><b>¡PERFECTO! +3</b></green> <gray>|</gray> <white>Puntos: ${data.score}</white>"))
            }
            1, 3 -> { // AMARILLO (Casi - Bien)
                data.score += 1
                p.playSound(p.location, Sound.ENTITY_ITEM_PICKUP, 1f, 1f)
                p.sendActionBar(plugin.messageManager.parse("<yellow><b>¡BIEN! +1</b></yellow> <gray>|</gray> <white>Puntos: ${data.score}</white>"))
            }
            0, 4 -> { // ROJO (Fallo)
                data.speedDelay = minOf(10, data.speedDelay + 2) // Penalización (Más lento)
                p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f)
                p.sendActionBar(plugin.messageManager.parse("<red><b>¡FALLO!</b></red> <gray>|</gray> <white>Puntos: ${data.score}</white>"))
            }
        }
    }

    override fun checkWinner() {
        if (playerData.isEmpty()) {
            stop(); return
        }

        val ptsRojo = players.filter { playerTeam[it] == EquipoCuerda.ROJO }.sumOf { playerData[it]?.score ?: 0 }
        val ptsAzul = players.filter { playerTeam[it] == EquipoCuerda.AZUL }.sumOf { playerData[it]?.score ?: 0 }

        val winnerTeam = if (ptsRojo > ptsAzul) EquipoCuerda.ROJO else EquipoCuerda.AZUL

        val msg = "<newline>${winnerTeam.color}<b>¡EL ${winnerTeam.nombre.uppercase()} HA GANADO!</b></${winnerTeam.color.replace("<#", "<")}> <white>Con una fuerza brutal.<newline>"
        plugin.server.broadcast(plugin.messageManager.parse(msg))

        players.filter { playerTeam[it] == winnerTeam }.forEach { p ->
            p.world.spawnParticle(org.bukkit.Particle.FIREWORK, p.location, 100)
            p.playSound(p.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        }

        stop()
    }

    override fun onStop() {
        HandlerList.unregisterAll(this)
        plugin.eventManager.currentGame = null
        var lobby = plugin.arenaManager.mainLobby
        if (lobby == null || lobby.world == null) lobby = plugin.server.worlds[0].spawnLocation

        (players + spectators).forEach { p ->
            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}

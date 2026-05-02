package pumpkin.eventos.games.iceboat

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Boat
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.scoreboard.Team
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

enum class RacePhase { VOTING, PREPARING, RACING, ENDED }

class IceBoatRacing(plugin: PumpkinEventos) : EventGame(plugin, "iceboat", "<#00FFFF>Ice Boat Racing</#00FFFF>") {

    var phase = RacePhase.VOTING
    var phaseTimer = 10
    private var gameTimer = 300
    private var overtimeGiven = false

    val votes = mutableMapOf<UUID, Int>()
    var totalLaps = 3

    val playerLaps = mutableMapOf<UUID, Int>()
    val playerCheckpoints = mutableMapOf<UUID, Int>()
    val playerColors = mutableMapOf<UUID, Color>()

    // --- SISTEMA DE TIEMPOS DE VUELTA ---
    val lapStartTimes = mutableMapOf<UUID, Long>()
    val bestLapTimes = mutableMapOf<UUID, Long>()

    var minX = 0; var maxX = 0
    var minZ = 0; var maxZ = 0

    private var ghostTeam: Team? = null
    val ganadores = mutableSetOf<UUID>()

    override fun getExtraPlaceholders(): Map<String, String> {
        val min = gameTimer / 60
        val sec = gameTimer % 60

        val topLaps = bestLapTimes.entries.sortedBy { it.value }.take(3)

        fun formatMs(ms: Long): String {
            val totalSecs = ms / 1000
            val m = totalSecs / 60
            val s = totalSecs % 60
            val mills = (ms % 1000) / 10
            return String.format("%02d:%02d:%02d", m, s, mills)
        }

        val top1 = topLaps.getOrNull(0)?.let { "${plugin.server.getPlayer(it.key)?.name ?: "Alguien"} <gray>-</gray> <#39FF14>${formatMs(it.value)}" } ?: "Nadie - 00:00:00"
        val top2 = topLaps.getOrNull(1)?.let { "${plugin.server.getPlayer(it.key)?.name ?: "Alguien"} <gray>-</gray> <#39FF14>${formatMs(it.value)}" } ?: "Nadie - 00:00:00"
        val top3 = topLaps.getOrNull(2)?.let { "${plugin.server.getPlayer(it.key)?.name ?: "Alguien"} <gray>-</gray> <#39FF14>${formatMs(it.value)}" } ?: "Nadie - 00:00:00"

        return mapOf(
            "%time_left%" to String.format("%02d:%02d", min, sec),
            "%vueltas_totales%" to totalLaps.toString(),
            "%vivos%" to (players.size - ganadores.size).toString(),
            "%lap_best_1%" to top1,
            "%lap_best_2%" to top2,
            "%lap_best_3%" to top3
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return mapOf(GameRule.FALL_DAMAGE to false, GameRule.DO_MOB_SPAWNING to false)
    }

    override fun onStart() {
        phase = RacePhase.VOTING
        phaseTimer = 10
        gameTimer = 300
        overtimeGiven = false
        votes.clear()
        playerLaps.clear()
        playerCheckpoints.clear()
        playerColors.clear()
        lapStartTimes.clear()
        bestLapTimes.clear()
        ganadores.clear()

        configurarEquiposFantasma()

        players.forEach { p ->
            p.gameMode = GameMode.ADVENTURE
            p.inventory.clear()
            val voteItem = ItemStack(Material.OAK_BOAT).apply {
                itemMeta = itemMeta?.apply { displayName(plugin.messageManager.parse("<#00FFFF><b>🏁 Votar Vueltas</b></#00FFFF>")) }
            }
            p.inventory.setItem(4, voteItem)

            ghostTeam?.addEntry(p.name)
            p.sendMessage(plugin.messageManager.parse("<yellow>Tienes 10 segundos para votar el número de vueltas.</yellow>"))
        }

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            when (phase) {
                RacePhase.VOTING -> {
                    players.forEach { it.sendActionBar(plugin.messageManager.parse("<#00FFFF>Votando vueltas: <b>$phaseTimer</b>s</#00FFFF>")) }
                    if (phaseTimer <= 0) prepararCarrera()
                    phaseTimer--
                }
                RacePhase.PREPARING -> {
                    if (phaseTimer in 1..3) {
                        val color = if (phaseTimer == 1) "<red>" else if (phaseTimer == 2) "<yellow>" else "<green>"
                        val title = Title.title(plugin.messageManager.parse("$color<bold>$phaseTimer</bold>"), Component.empty())
                        players.forEach { it.showTitle(title); it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f) }
                    } else if (phaseTimer <= 0) {
                        phase = RacePhase.RACING
                        val title = Title.title(plugin.messageManager.parse("<#39FF14><bold>¡GO!</bold></#39FF14>"), Component.empty())
                        players.forEach { it.showTitle(title); it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f) }
                    }
                    phaseTimer--
                }
                RacePhase.RACING -> {
                    gameTimer--
                    if (players.size <= ganadores.size) {
                        task.cancel()
                        plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                        return@runAtFixedRate
                    }

                    if (gameTimer <= 0) {
                        if (ganadores.isEmpty() && !overtimeGiven) {
                            overtimeGiven = true
                            gameTimer = 120

                            val overMsg = plugin.messageManager.parse("<newline><#FF3131><b>¡TIEMPO AGOTADO!</b></#FF3131> <white>Pero como nadie ha llegado a la meta... ¡Se otorgan <yellow>2 MINUTOS EXTRA</yellow> de prórroga!</white><newline>")
                            plugin.server.broadcast(overMsg)

                            players.forEach { it.playSound(it.location, Sound.ENTITY_WITHER_SPAWN, 1f, 1f) }
                        } else {
                            task.cancel()
                            plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                        }
                    }
                }
                RacePhase.ENDED -> task.cancel()
            }
        }, 1, 1, TimeUnit.SECONDS)

        iniciarMecanicasCarrera()
    }

    private fun configurarEquiposFantasma() {
        val sb = Bukkit.getScoreboardManager().mainScoreboard
        ghostTeam = sb.getTeam("GhostRacer") ?: sb.registerNewTeam("GhostRacer")
        ghostTeam?.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)
    }

    private fun prepararCarrera() {
        val arena = currentArena ?: return
        val targetWorld = gameWorld ?: return

        val tally = votes.values.groupingBy { it }.eachCount()
        totalLaps = tally.maxByOrNull { it.value }?.key ?: 3
        gameTimer = totalLaps * 120

        plugin.server.broadcast(plugin.messageManager.parse("<newline><#00FFFF><b>🏁 ICE BOAT RACING</b></#00FFFF> <white>» ¡La carrera será de <b>$totalLaps vueltas</b>!</white><newline>"))

        phase = RacePhase.PREPARING
        phaseTimer = 5

        val posA = arena.dueloA ?: return
        val posB = arena.dueloB ?: return
        minX = min(posA.blockX, posB.blockX); maxX = max(posA.blockX, posB.blockX)
        minZ = min(posA.blockZ, posB.blockZ); maxZ = max(posA.blockZ, posB.blockZ)

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            val spawns = arena.spawnPoints
            var i = 0

            players.forEach { p ->
                p.inventory.clear()
                playerLaps[p.uniqueId] = 0
                playerCheckpoints[p.uniqueId] = 0
                playerColors[p.uniqueId] = Color.fromRGB((0..255).random(), (0..255).random(), (0..255).random())

                if (spawns.isNotEmpty()) {
                    val loc = spawns[i % spawns.size].clone().apply { world = targetWorld }
                    i++

                    p.teleportAsync(loc).thenAccept {
                        plugin.server.regionScheduler.runDelayed(plugin, loc, { _ ->
                            val boat = targetWorld.spawnEntity(loc, EntityType.CHERRY_BOAT) as Boat
                            boat.addPassenger(p)

                            // OpenBoatUtils Support
                            pumpkin.eventos.utils.OpenBoatUtilsManager.sendTenStepInterpolation(plugin, p, true)
                            pumpkin.eventos.utils.OpenBoatUtilsManager.sendExclusiveMode(plugin, p, pumpkin.eventos.utils.OpenBoatUtilsManager.MODE_RALLY)
                            pumpkin.eventos.utils.OpenBoatUtilsManager.sendCollisionMode(plugin, p, pumpkin.eventos.utils.OpenBoatUtilsManager.COLLISION_NO_BOATS_OR_PLAYERS)
                        }, 5L)
                    }
                }
            }
        }, 10L)
    }

    fun abrirMenuVotos(p: Player) {
        val gui = Gui.gui().title(Component.text("§8Votar Vueltas")).rows(1).disableAllInteractions().create()
        val opciones = listOf(1, 3, 5)
        val slots = listOf(2, 4, 6)

        opciones.forEachIndexed { i, vueltas ->
            val item = ItemBuilder.from(Material.MINECART).name(plugin.messageManager.parse("<#00FFFF><b>$vueltas Vueltas</b>"))
                .lore(plugin.messageManager.parse("<gray>Haz click para votar.</gray>"))
                .flags(ItemFlag.HIDE_ATTRIBUTES)
                .asGuiItem { _ ->
                    votes[p.uniqueId] = vueltas
                    p.sendMessage(plugin.messageManager.parse("<green>Has votado por $vueltas vueltas.</green>"))
                    p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
                    gui.close(p)
                }
            gui.setItem(slots[i], item)
        }
        gui.open(p)
    }

    private fun iniciarMecanicasCarrera() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (phase == RacePhase.ENDED || !isRunning) { task.cancel(); return@runAtFixedRate }
            if (phase == RacePhase.RACING) {

                val currentTime = System.currentTimeMillis()

                players.forEach { p ->
                    if (!ganadores.contains(p.uniqueId)) {
                        val boat = p.vehicle as? Boat ?: return@forEach
                        val color = playerColors[p.uniqueId] ?: Color.AQUA

                        val startTime = lapStartTimes[p.uniqueId]
                        val lapTimeStr = if (startTime != null) {
                            formatMs(currentTime - startTime)
                        } else "00:00:00"

                        boat.world.spawnParticle(Particle.DUST, boat.location.add(0.0, 0.5, 0.0), 5, 0.2, 0.2, 0.2, Particle.DustOptions(color, 1.5f))

                        // Solo mostramos el tiempo de vuelta
                        p.sendActionBar(plugin.messageManager.parse("<green>Tiempo de tu vuelta: $lapTimeStr</green>"))
                    }
                }
            }
        }, 2L, 2L)
    }

    fun markWinner(p: Player) {
        if (ganadores.contains(p.uniqueId)) return
        ganadores.add(p.uniqueId)

        val pos = ganadores.size
        plugin.server.broadcast(plugin.messageManager.parse("<#00FFFF>🏁 <b>${p.name}</b></#00FFFF> <white>terminó la carrera en <b>#$pos</b> lugar.</white>"))
        p.playSound(p.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)

        val puntos = max(1, 10 - pos)
        plugin.puntajeManager.addPoints(p, puntos, "Top #$pos en Ice Boat")

        val arena = currentArena
        val gradaLoc = arena?.gradas?.clone() ?: arena?.centerLocation?.clone()
        if (gradaLoc != null) {
            gradaLoc.world = p.world
            p.teleportAsync(gradaLoc)
        }
    }

    fun formatMs(ms: Long): String {
        val totalSecs = ms / 1000
        val m = totalSecs / 60
        val s = totalSecs % 60
        val mills = (ms % 1000) / 10
        return String.format("%02d:%02d:%02d", m, s, mills)
    }

    override fun checkWinner() {
        val winnersStr = if (ganadores.isEmpty()) "Nadie" else "${ganadores.size} corredores"
        val rawWin = plugin.languageManager.get("iceboat.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winnersStr)))
        stop()
    }

    override fun onStop() {
        phase = RacePhase.ENDED
        ghostTeam?.unregister()
        plugin.eventManager.currentGame = null
        val lobby = plugin.arenaManager.mainLobby ?: plugin.server.worlds[0].spawnLocation

        (players + spectators).forEach { p ->
            p.vehicle?.remove()
            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            
            // OpenBoatUtils Support Reset
            pumpkin.eventos.utils.OpenBoatUtilsManager.sendReset(plugin, p)
            
            p.teleportAsync(lobby)
        }
    }
}

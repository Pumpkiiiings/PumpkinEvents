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
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

enum class RacePhase { VOTING, PREPARING, RACING, ENDED }

class IceBoatRacing(plugin: PumpkinEventos) : EventGame(plugin, "iceboat", "<#00FFFF>Ice Boat Racing</#00FFFF>") {

    var phase = RacePhase.VOTING
    var phaseTimer = 10
    private var gameTimer = 300

    val votes = mutableMapOf<UUID, Int>()
    var totalLaps = 3

    val playerLaps = mutableMapOf<UUID, Int>()
    val playerCheckpoints = mutableMapOf<UUID, Int>()
    val playerColors = mutableMapOf<UUID, Color>()

    var minX = 0; var maxX = 0
    var minZ = 0; var maxZ = 0

    private var ghostTeam: Team? = null

    override fun getExtraPlaceholders(): Map<String, String> {
        val min = gameTimer / 60
        val sec = gameTimer % 60
        return mapOf(
            "%time_left%" to String.format("%02d:%02d", min, sec),
            "%vueltas_totales%" to totalLaps.toString()
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return mapOf(GameRule.FALL_DAMAGE to false, GameRule.DO_MOB_SPAWNING to false)
    }

    override fun onStart() {
        phase = RacePhase.VOTING
        phaseTimer = 10
        gameTimer = 300
        votes.clear()
        playerLaps.clear()
        playerCheckpoints.clear()
        playerColors.clear()

        configurarEquiposFantasma()

        // Entregar el ítem de votación a todos en el Lobby
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
                    // --- NUEVO: SEMÁFORO VISUAL ---
                    val times = Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO)
                    val subTitle = plugin.messageManager.parse("<white>¡Arranca cuando cambie a rojo!</white>")

                    when (phaseTimer) {
                        5 -> mostrarSemaforo("<dark_gray>● ● ● ● ●</dark_gray>", subTitle, times, Sound.BLOCK_NOTE_BLOCK_BASS)
                        4 -> mostrarSemaforo("<dark_gray>● ● ● ● ●</dark_gray>", subTitle, times, Sound.ENTITY_EXPERIENCE_ORB_PICKUP)
                        3 -> mostrarSemaforo("<green>●</green> <dark_gray>● ● ● ●</dark_gray>", subTitle, times, Sound.BLOCK_NOTE_BLOCK_HAT)
                        2 -> mostrarSemaforo("<green>●</green> <yellow>●</yellow> <dark_gray>● ● ●</dark_gray>", subTitle, times, Sound.BLOCK_NOTE_BLOCK_HAT)
                        1 -> mostrarSemaforo("<green>●</green> <yellow>●</yellow> <yellow>●</yellow> <dark_gray>● ●</dark_gray>", subTitle, times, Sound.BLOCK_NOTE_BLOCK_HAT)
                        0 -> {
                            phase = RacePhase.RACING
                            val startTitle = Title.title(
                                plugin.messageManager.parse("<red>●</red> <red>●</red> <red>●</red> <red>●</red> <red>●</red>"),
                                plugin.messageManager.parse("<#FF3131><bold>¡GO!</bold></#FF3131>"),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
                            )
                            players.forEach {
                                it.showTitle(startTitle)
                                it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                            }
                        }
                    }
                    phaseTimer--
                }
                RacePhase.RACING -> {
                    gameTimer--
                    if (gameTimer <= 0) {
                        task.cancel()
                        plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                    }
                }
                RacePhase.ENDED -> task.cancel()
            }
        }, 1, 1, TimeUnit.SECONDS)

        iniciarTrails()
    }

    private fun mostrarSemaforo(texto: String, sub: Component, times: Title.Times, sonido: Sound) {
        val title = Title.title(plugin.messageManager.parse(texto), sub, times)
        players.forEach { p ->
            p.showTitle(title)
            p.playSound(p.location, sonido, 1f, 1f)
        }
    }

    private fun configurarEquiposFantasma() {
        val sb = Bukkit.getScoreboardManager().mainScoreboard
        ghostTeam = sb.getTeam("GhostRacer") ?: sb.registerNewTeam("GhostRacer")
        ghostTeam?.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)
    }

    private fun prepararCarrera() {
        val arena = currentArena ?: return
        val targetWorld = gameWorld?: return

        val tally = votes.values.groupingBy { it }.eachCount()
        totalLaps = tally.maxByOrNull { it.value }?.key ?: 3

        plugin.server.broadcast(plugin.messageManager.parse("<newline><#00FFFF><b>🏁 ICE BOAT RACING</b></#00FFFF> <white>» ¡La carrera será de <b>$totalLaps vueltas</b>!<newline>"))

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

                            // FIX 1.21: Usar Boat.Type en lugar de TreeSpecies
                            boat.boatType = Boat.Type.CHERRY

                            boat.addPassenger(p)
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

    private fun iniciarTrails() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (phase == RacePhase.ENDED || !isRunning) { task.cancel(); return@runAtFixedRate }
            if (phase == RacePhase.RACING) {
                players.forEach { p ->
                    val boat = p.vehicle ?: return@forEach
                    val color = playerColors[p.uniqueId] ?: Color.AQUA
                    boat.world.spawnParticle(Particle.DUST, boat.location.add(0.0, 0.5, 0.0), 5, 0.2, 0.2, 0.2, Particle.DustOptions(color, 1.5f))
                }
            }
        }, 2L, 2L)
    }

    fun markWinner(p: Player) {
        val rawWin = plugin.languageManager.get("iceboat.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", p.name)))
        p.world.spawnParticle(Particle.FIREWORK, p.location, 100)
        p.playSound(p.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        plugin.puntajeManager.addPoints(p, 15, "¡Primer Lugar en Ice Boat!")
        stop()
    }

    override fun checkWinner() {
        val winner = playerLaps.maxByOrNull { it.value }?.key?.let { plugin.server.getPlayer(it) } ?: players.firstOrNull() ?: return
        markWinner(winner)
    }

    override fun onStop() {
        phase = RacePhase.ENDED
        ghostTeam?.unregister()

        plugin.eventManager.currentGame = null
        var lobby = plugin.arenaManager.mainLobby
        if (lobby == null || lobby.world == null) lobby = plugin.server.worlds[0].spawnLocation

        (players + spectators).forEach { p ->
            p.vehicle?.remove()
            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}

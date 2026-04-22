package pumpkin.eventos.hud

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.hooks.LuckPermsHook
import java.util.*
import java.util.concurrent.TimeUnit

class BoardManager(private val plugin: PumpkinEventos) {

    private val playerBossBars = mutableMapOf<UUID, BossBar>()
    private val lp = LuckPermsHook()

    private var currentTitle: String = ""
    private var tabFormat: String = ""

    fun startTasks() {
        reload()

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { _ ->
            val onlinePlayers = Bukkit.getOnlinePlayers().toList()
            if (onlinePlayers.isEmpty()) return@runAtFixedRate

            val playersCountStr = onlinePlayers.size.toString()
            val playerTabNames = mutableMapOf<Player, Component>()
            val playerSortingTeams = mutableMapOf<String, String>()

            val currentGame = plugin.eventManager.currentGame

            for (target in onlinePlayers) {
                val prefix = lp.getPrefix(target)
                val suffix = lp.getSuffix(target)
                val weight = 1000 - lp.getWeight(target)

                val formatStr = tabFormat
                    .replace("<prefix>", prefix)
                    .replace("<suffix>", suffix)
                    .replace("<player>", target.name)

                playerTabNames[target] = plugin.messageManager.parse(target, formatStr)
                playerSortingTeams[target.name] = String.format("%04d_%s", weight, target.name)
            }

            for (player in onlinePlayers) {
                val modeName = currentGame?.displayName ?: "LOBBY"
                val aliveStr = currentGame?.players?.size?.toString() ?: "0"

                // --- 1. REEMPLAZO DE VARIABLES DINÁMICO ---
                val replaceVars = { text: String ->
                    var processed = text
                        .replace("%players%", playersCountStr)
                        .replace("%mode%", modeName)
                        .replace("%alive%", aliveStr)

                    // Si el juego está corriendo, pedimos sus variables extra (Ej: luchador1 de Sumo)
                    if (currentGame != null) {
                        currentGame.getExtraPlaceholders().forEach { (key, value) ->
                            processed = processed.replace(key, value)
                        }
                    }
                    processed
                }

                // --- 2. TABLIST ---
                val headerStr = plugin.config.getString("tab.header") ?: ""
                val footerStr = plugin.config.getString("tab.footer") ?: ""
                player.sendPlayerListHeaderAndFooter(
                    plugin.messageManager.parse(replaceVars(headerStr)),
                    plugin.messageManager.parse(replaceVars(footerStr))
                )

                // --- 3. BOSSBAR (0 Hardcode) ---
                updateBossBar(player, currentGame != null, replaceVars)

                // --- 4. SCOREBOARD (Lee de messages.yml) ---
                val lang = plugin.languageManager
                val lines = if (currentGame == null) {
                    lang.getScoreboardList("scoreboard.waiting")
                } else {
                    // Busca un score específico (ej: ingame_sumo), si no existe usa ingame_default
                    var scoreList = lang.getScoreboardList("scoreboard.ingame_${currentGame.id}")
                    if (scoreList.isEmpty()) scoreList = lang.getScoreboardList("scoreboard.ingame_default")
                    scoreList
                }

                val parsedLines = lines.map { plugin.messageManager.parse(replaceVars(it)) }
                val titleComp = plugin.messageManager.parse(currentTitle)
                val myTabName = playerTabNames[player]

// Hilo nativo del jugador
                player.scheduler.run(plugin, { _ ->
                    if (myTabName != null) player.playerListName(myTabName)

                    var board = player.scoreboard
                    if (board == Bukkit.getScoreboardManager().mainScoreboard) {
                        board = Bukkit.getScoreboardManager().newScoreboard
                        player.scoreboard = board
                    }

                    // 1. Limpiar completamente el Scoreboard cada vez que cambia de estado (Waiting -> Ingame)
                    val objective = board.getObjective("event_board") ?: board.registerNewObjective("event_board", Criteria.DUMMY, titleComp).apply {
                        displaySlot = DisplaySlot.SIDEBAR
                    }
                    objective.displayName(titleComp)

                    // 2. Limpiar las líneas basura viejas de Minecraft
                    board.entries.forEach { entry ->
                        board.resetScores(entry)
                    }

                    // 3. Asignar las nuevas líneas usando identificadores invisibles limpios
                    val colorCodes = arrayOf("§a", "§b", "§c", "§d", "§e", "§f", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9")
                    var scoreIndex = parsedLines.size

                    for (i in parsedLines.indices) {
                        if (i >= colorCodes.size) break

                        val teamName = "line_$i"
                        val team = board.getTeam(teamName) ?: board.registerNewTeam(teamName)

                        val entryId = colorCodes[i] + "§r" // Genera una key única: Ej. "§a§r"

                        if (!team.hasEntry(entryId)) {
                            team.addEntry(entryId)
                        }

                        team.prefix(parsedLines[i])
                        objective.getScore(entryId).score = scoreIndex
                        scoreIndex--
                    }

                    // 4. Ordenar el TAB
                    playerSortingTeams.forEach { (targetName, teamName) ->
                        val sortingTeam = board.getTeam(teamName) ?: board.registerNewTeam(teamName)
                        if (!sortingTeam.hasEntry(targetName)) sortingTeam.addEntry(targetName)
                    }
                }, null)
            }
        }, 0, 1, TimeUnit.SECONDS)
    }

    fun reload() {
        currentTitle = plugin.languageManager.getScoreboard("scoreboard.title")
        if (currentTitle.isEmpty()) currentTitle = "<gradient:gold:yellow><b>PUMPKIN EVENTOS</b></gradient>"

        tabFormat = plugin.config.getString("tab.player-format") ?: "<prefix><player>"

        playerBossBars.forEach { (uuid, bar) -> Bukkit.getPlayer(uuid)?.hideBossBar(bar) }
        playerBossBars.clear()
    }

    private fun updateBossBar(player: Player, isGameRunning: Boolean, replaceVars: (String) -> String) {
        val lang = plugin.languageManager
        val currentGame = plugin.eventManager.currentGame

        // Lee la bossbar desde messages.yml
        var configText = if (isGameRunning) {
            var text = lang.getScoreboard("bossbar.ingame_${currentGame?.id}")
            if (text.isEmpty()) text = lang.getScoreboard("bossbar.ingame_default")
            text
        } else {
            lang.getScoreboard("bossbar.waiting")
        }

        if (configText.contains("Error")) configText = "%mode%" // Fallback seguro

        val parsedContent = plugin.messageManager.parse(replaceVars(configText))

        val bar = playerBossBars.getOrPut(player.uniqueId) {
            val newBar = BossBar.bossBar(parsedContent, 1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS)
            player.showBossBar(newBar)
            newBar
        }
        bar.name(parsedContent)
    }
}

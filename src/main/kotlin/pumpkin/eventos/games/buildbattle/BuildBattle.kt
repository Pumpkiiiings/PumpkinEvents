package pumpkin.eventos.games.buildbattle

import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class BbPhase { BUILD, VOTING, ENDED }

class BuildBattle(
    plugin: PumpkinEventos,
    val isTeams: Boolean
) : EventGame(
    plugin,
    if (isTeams) "buildbattle_teams" else "buildbattle_solo",
    if (isTeams) "<#FF9900>Build Battle Equipos</#FF9900>" else "<#FF9900>Build Battle</#FF9900>"
) {
    var phase = BbPhase.BUILD
    var buildTimer = 300         // 5 minutos
    var voteTimer = 30           // 30s por parcela
    var currentVotePlot = -1     // índice de la parcela siendo votada
    val plotSpawns = mutableListOf<Location>()   // spawns de parcelas (addplot)
    val playerPlot = mutableMapOf<UUID, Int>()   // jugador → índice parcela
    val plotVotes = mutableMapOf<Int, Int>()     // índice parcela → total votos positivos
    val alreadyVoted = mutableSetOf<UUID>()

    // Equipos (misma lógica que Skywars Duos)
    val playerTeam = mutableMapOf<Player, Int>()

    override fun getCustomGameRules(): Map<GameRule<*>, Any> =
        mapOf(GameRule.DO_MOB_SPAWNING to false, GameRule.FALL_DAMAGE to false)

    override fun getExtraPlaceholders(): Map<String, String> {
        val min = buildTimer / 60
        val sec = buildTimer % 60
        return mapOf(
            "%bb_phase%" to phase.name,
            "%bb_timer%" to String.format("%02d:%02d", min, sec),
            "%bb_vivos%" to players.size.toString()
        )
    }

    override fun onStart() {
        phase = BbPhase.BUILD
        buildTimer = 300
        voteTimer = 30
        currentVotePlot = -1
        playerPlot.clear()
        plotVotes.clear()
        alreadyVoted.clear()
        playerTeam.clear()

        // Recargar spawns de parcelas desde la arena
        plotSpawns.clear()
        val arena = currentArena ?: return
        val targetWorld = gameWorld ?: return
        plotSpawns.addAll(arena.spawnPoints.map { it.clone().apply { world = targetWorld } })

        // Si modo equipos, iniciar selección de equipo igual a Skywars
        if (isTeams) {
            iniciarSeleccionEquipos(targetWorld)
        } else {
            iniciarBuild(targetWorld)
        }
    }

    private fun iniciarSeleccionEquipos(world: org.bukkit.World) {
        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            players.forEach { p ->
                p.gameMode = GameMode.ADVENTURE
                p.inventory.clear()
                val selector = ItemStack(Material.BLAZE_POWDER).apply {
                    itemMeta = itemMeta?.apply {
                        displayName(plugin.messageManager.parse("<#FF9900><b>🚩 Seleccionar Equipo</b></#FF9900>"))
                    }
                }
                p.inventory.setItem(4, selector)
                p.sendMessage(plugin.messageManager.parse("<yellow>Tienes 15 segundos para elegir equipo.</yellow>"))
            }
        }, 10L)

        var countdown = 15
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }
            countdown--
            players.forEach { it.sendActionBar(plugin.messageManager.parse("<yellow>Selección de equipos: <b>${countdown}s</b></yellow>")) }
            if (countdown <= 0) {
                task.cancel()
                agruparEquipos()
                plugin.server.globalRegionScheduler.run(plugin) { _ -> iniciarBuild(world) }
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun agruparEquipos() {
        val unassigned = players.filter { !playerTeam.containsKey(it) }.toMutableList()
        var teamId = playerTeam.values.maxOrNull() ?: 0
        while (unassigned.isNotEmpty()) {
            teamId++
            val p1 = unassigned.removeAt(0)
            playerTeam[p1] = teamId
            if (unassigned.isNotEmpty()) {
                val p2 = unassigned.removeAt(0)
                playerTeam[p2] = teamId
                p1.sendMessage(plugin.messageManager.parse("<green>Tu compañero es: <yellow>${p2.name}"))
                p2.sendMessage(plugin.messageManager.parse("<green>Tu compañero es: <yellow>${p1.name}"))
            }
        }
    }

    private fun iniciarBuild(world: org.bukkit.World) {
        // Asignar una parcela a cada jugador (o equipo)
        val plotAssignedPlayers = if (isTeams) {
            players.groupBy { playerTeam[it] ?: 0 }.values.toList()
        } else {
            players.map { listOf(it) }
        }

        plotAssignedPlayers.forEachIndexed { idx, group ->
            val spawn = plotSpawns.getOrNull(idx % plotSpawns.size.coerceAtLeast(1)) ?: return@forEachIndexed
            // El spawn guardado es el bloque de oro (suelo). Teleportamos 1 bloque arriba para que el jugador parezca encima.
            val loc = spawn.clone().apply { this.world = world; y += 1.0 }
            group.forEach { p ->
                playerPlot[p.uniqueId] = idx
                plotVotes[idx] = 0
                p.teleportAsync(loc).thenAccept {
                    p.gameMode = GameMode.CREATIVE
                    p.inventory.clear()
                    darPalo(p)
                    p.sendMessage(plugin.messageManager.parse("<#FF9900><b>¡Construye en tu parcela!</b></#FF9900> <white>Tienes <yellow>5 minutos</yellow>.</white>"))
                }
            }
        }

        plugin.server.broadcast(plugin.messageManager.parse("\n<#FF9900><b>🏗 BUILD BATTLE</b></#FF9900> <white>» ¡Comiencen a construir! Tienen <yellow>5 minutos</yellow>.\n"))

        // Timer de construcción
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || phase != BbPhase.BUILD) { task.cancel(); return@runAtFixedRate }
            buildTimer--

            val min = buildTimer / 60
            val sec = buildTimer % 60
            players.forEach { it.sendActionBar(plugin.messageManager.parse("<#FF9900>⏳ Construcción: <b>${String.format("%02d:%02d", min, sec)}</b></#FF9900>")) }

            if (buildTimer in listOf(60, 30, 10)) {
                plugin.server.broadcast(plugin.messageManager.parse("<yellow>⏰ Quedan <b>${buildTimer}s</b> para construir."))
            }

            if (buildTimer <= 0) {
                task.cancel()
                plugin.server.globalRegionScheduler.run(plugin) { _ -> iniciarVotacion() }
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    internal fun darPalo(p: Player) {
        val cfg = plugin.config
        val radius = cfg.getInt("buildbattle.plot_radius", 15)
        val palo = ItemStack(Material.STICK).apply {
            val meta = itemMeta ?: return@apply
            meta.displayName(plugin.messageManager.parse("<#00FFFF><b>✏ Varita de Selección</b></#00FFFF>"))
            meta.lore(listOf(
                plugin.messageManager.parse("<gray>Clic Izq en bloque → Pos1"),
                plugin.messageManager.parse("<gray>Clic Der en bloque → Pos2"),
                plugin.messageManager.parse("<gray>Clic Der al aire con bloque → Rellenar"),
                plugin.messageManager.parse("<dark_gray>Radio parcela: ±$radius bloques")
            ))
            val key = org.bukkit.NamespacedKey(plugin, "bb_item")
            meta.persistentDataContainer.set(key, org.bukkit.persistence.PersistentDataType.STRING, "wand")
            itemMeta = meta
        }
        p.inventory.setItem(8, palo)
    }

    private fun iniciarVotacion() {
        phase = BbPhase.VOTING
        players.forEach { p ->
            p.gameMode = GameMode.ADVENTURE
            p.allowFlight = true
            p.isFlying = true
            p.inventory.clear()
            // Dar items de voto
            val voteYes = ItemStack(Material.LIME_DYE).apply {
                itemMeta = itemMeta?.apply { displayName(plugin.messageManager.parse("<green><b>👍 Votar Positivo</b></green>")) }
            }
            val voteNo = ItemStack(Material.RED_DYE).apply {
                itemMeta = itemMeta?.apply { displayName(plugin.messageManager.parse("<red><b>👎 Votar Negativo</b></red>")) }
            }
            p.inventory.setItem(3, voteYes)
            p.inventory.setItem(5, voteNo)
        }

        votar(0)
    }

    private fun votar(plotIdx: Int) {
        if (!isRunning) return
        val plotList = plotVotes.keys.sorted()
        if (plotIdx >= plotList.size) {
            plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
            return
        }

        currentVotePlot = plotList[plotIdx]
        alreadyVoted.clear()
        voteTimer = 30

        // Teletransportar a todos frente a la parcela actual
        val targetWorld = gameWorld ?: return
        val plotLoc = plotSpawns.getOrNull(currentVotePlot % plotSpawns.size.coerceAtLeast(1))?.clone()?.apply { world = targetWorld }
        if (plotLoc != null) {
            val viewLoc = plotLoc.clone().add(15.0, 3.0, 0.0).apply { yaw = 270f }
            players.forEach { it.teleportAsync(viewLoc) }
        }

        val builder = players.firstOrNull { playerPlot[it.uniqueId] == currentVotePlot }?.name ?: "Parcela #${currentVotePlot + 1}"
        plugin.server.broadcast(plugin.messageManager.parse("\n<#FF9900><b>VOTACIÓN</b></#FF9900> <white>» ¡Voten la construcción de <yellow>$builder</yellow>!\n"))

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning || phase != BbPhase.VOTING) { task.cancel(); return@runAtFixedRate }
            voteTimer--
            players.forEach { it.sendActionBar(plugin.messageManager.parse("<#FF9900>🗳 Votando parcela de <b>$builder</b> — <white>${voteTimer}s</white>")) }

            if (voteTimer <= 0) {
                task.cancel()
                plugin.server.globalRegionScheduler.run(plugin) { _ -> votar(plotIdx + 1) }
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    fun registerVote(voter: Player, positive: Boolean) {
        if (phase != BbPhase.VOTING) return
        if (alreadyVoted.contains(voter.uniqueId)) {
            voter.sendMessage(plugin.messageManager.parse("<red>¡Ya votaste en esta ronda!"))
            return
        }
        val builderUUID = playerPlot.entries.firstOrNull { it.value == currentVotePlot }?.key
        if (builderUUID == voter.uniqueId) {
            voter.sendMessage(plugin.messageManager.parse("<red>¡No puedes votar por tu propia construcción!"))
            return
        }
        alreadyVoted.add(voter.uniqueId)
        if (positive) {
            plotVotes[currentVotePlot] = (plotVotes[currentVotePlot] ?: 0) + 1
            voter.playSound(voter.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
            voter.sendMessage(plugin.messageManager.parse("<green>✔ Votaste positivo."))
        } else {
            voter.playSound(voter.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            voter.sendMessage(plugin.messageManager.parse("<red>✘ Votaste negativo."))
        }
    }

    override fun checkWinner() {
        phase = BbPhase.ENDED
        val winner = plotVotes.maxByOrNull { it.value }
        val winnerName = if (winner != null) {
            val uid = playerPlot.entries.firstOrNull { it.value == winner.key }?.key
            players.firstOrNull { it.uniqueId == uid }?.name ?: "Alguien"
        } else "Nadie"

        val winnerVotes = winner?.value ?: 0
        plugin.server.broadcast(plugin.messageManager.parse("\n<#FF9900><b>BUILD BATTLE</b></#FF9900> <white>» ¡<yellow>$winnerName</yellow> ganó con <green>$winnerVotes votos</green>!\n"))

        val winnerPlayer = players.firstOrNull { it.name == winnerName }
        winnerPlayer?.let {
            it.playSound(it.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
            plugin.puntajeManager.addPoints(it, 15, "¡Victoria en Build Battle!")
        }
        stop()
    }

    override fun onStop() {
        phase = BbPhase.ENDED
        plugin.eventManager.currentGame = null
        val lobby = plugin.arenaManager.mainLobby ?: plugin.server.worlds[0].spawnLocation
        (players + spectators).forEach { p ->
            p.inventory.clear()
            p.allowFlight = false
            p.isFlying = false
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}

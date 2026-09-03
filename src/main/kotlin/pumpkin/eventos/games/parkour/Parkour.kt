package pumpkin.eventos.games.parkour

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import pumpkin.eventos.games.support.TeamAssignmentService
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

enum class ParkourPhase { SELECTING, PRESTART, PLAYING, ENDED }

class Parkour(plugin: PumpkinEventos, val isDuos: Boolean) : EventGame(
    plugin,
    if (isDuos) "parkour_duos" else "parkour",
    if (isDuos) plugin.languageManager.get("parkour.display_duos").ifBlank { "<#39FF14>Parkour Dúos</#39FF14>" }
    else        plugin.languageManager.get("parkour.display_solo").ifBlank { "<#39FF14>Parkour</#39FF14>" }
) {

    var phase = ParkourPhase.SELECTING
    var preStartTimer = 10
    var gameTimer = 300

    // Teams (in solo each player has their own teamId)
    val playerTeam    = mutableMapOf<Player, Int>()
    val teamLeader    = mutableMapOf<Int, Player>()
    val teamMembers   = mutableMapOf<Int, MutableList<Player>>()
    val teamCheckpoint = mutableMapOf<Int, Int>()    // teamId → checkpoint index reached
    val finishedTeams = mutableListOf<Int>()         // ordered

    val checkpoints: List<Location> get() = currentArena?.chairs ?: emptyList()

    private val wallBlocks = mutableListOf<Pair<Location, BlockData>>()
    val hidingPlayers = mutableSetOf<Player>()
    val pendingRequests = mutableMapOf<Player, Player>()   // teammate → leader

    private var teamSelectTimer = 15

    // ── Placeholders ──────────────────────────────────────────────────────────
    override fun getExtraPlaceholders(): Map<String, String> {
        val min = gameTimer / 60; val sec = gameTimer % 60
        val pts = plugin.config.getIntegerList("parkour.points")
        fun teamLabel(idx: Int): String {
            val tId = finishedTeams.getOrNull(idx) ?: return "-"
            return teamMembers[tId]?.joinToString(" & ") { it.name } ?: "-"
        }
        return mapOf(
            "%pk_timer%"      to String.format("%02d:%02d", min, sec),
            "%pk_vivos%"      to players.size.toString(),
            "%pk_modo%"       to if (isDuos) "Dúos" else "Solo",
            "%pk_ganadores%"  to finishedTeams.size.toString(),
            "%pk_max_win%"    to plugin.config.getInt("parkour.max_winners", 5).toString(),
            "%pk_top1%"       to teamLabel(0),
            "%pk_top2%"       to teamLabel(1),
            "%pk_top3%"       to teamLabel(2)
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> = mapOf(
        GameRule.FALL_DAMAGE     to !plugin.config.getBoolean("parkour.cancel_fall_damage", true),
        GameRule.DO_MOB_SPAWNING to false,
        GameRule.KEEP_INVENTORY  to true
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  onStart
    // ─────────────────────────────────────────────────────────────────────────
    override fun onStart() {
        phase        = ParkourPhase.SELECTING
        preStartTimer = plugin.config.getInt("parkour.prestart_time", 10)
        gameTimer    = plugin.config.getInt("parkour.timer", 300)
        teamSelectTimer = plugin.config.getInt("parkour.team_select_time", 15)

        playerTeam.clear(); teamLeader.clear(); teamMembers.clear()
        teamCheckpoint.clear(); finishedTeams.clear()
        wallBlocks.clear(); hidingPlayers.clear(); pendingRequests.clear()

        val arena = currentArena ?: return
        val world  = gameWorld  ?: return

        buildWall(world)

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            val center = arena.centerLocation?.clone()?.apply { this.world = world }
                ?: world.spawnLocation

            if (isDuos) {
                players.forEach { p ->
                    p.teleportAsync(center)
                    p.gameMode = GameMode.ADVENTURE
                    p.inventory.clear()
                    giveTeamSelectItem(p)
                    p.sendMessage(plugin.messageManager.parse(
                        plugin.languageManager.get("parkour.team_select_hint")
                            .ifBlank { "<yellow>Tienes ${teamSelectTimer}s para elegir equipo.</yellow>" }
                            .replace("%time%", teamSelectTimer.toString())
                    ))
                }
            } else {
                // Solo → assign teams immediately, jump to PRESTART
                players.forEachIndexed { i, p ->
                    val tId = i + 1
                    playerTeam[p] = tId
                    teamLeader[tId] = p
                    teamMembers[tId] = mutableListOf(p)
                    teamCheckpoint[tId] = 0
                }
                players.forEach { p ->
                    p.teleportAsync(center)
                    p.gameMode = GameMode.ADVENTURE
                    p.inventory.clear()
                }
                phase = ParkourPhase.PRESTART
            }
        }, 10L)

        // ── Timer ─────────────────────────────────────────────────────────────
        runAtFixedRate( { task ->

            when (phase) {

                ParkourPhase.SELECTING -> {
                    teamSelectTimer--
                    val msg = plugin.languageManager.get("parkour.actionbar.team_select")
                        .ifBlank { "<yellow>Selección de equipos: <b>%time%s</b></yellow>" }
                        .replace("%time%", teamSelectTimer.toString())
                    players.forEach { it.sendActionBar(plugin.messageManager.parse(msg)) }
                    if (teamSelectTimer <= 0) {
                        agruparEquipos()
                        preStartTimer = plugin.config.getInt("parkour.prestart_time", 10)
                        phase = ParkourPhase.PRESTART
                        plugin.server.globalRegionScheduler.run(plugin) { _ ->
                            teleportToSpawns(world, arena.spawnPoints)
                        }
                    }
                }

                ParkourPhase.PRESTART -> {
                    preStartTimer--
                    val actionMsg = plugin.languageManager.get("parkour.actionbar.prestart")
                        .ifBlank { "<#39FF14>El Parkour comienza en: <b>%time%s</b></#39FF14>" }
                        .replace("%time%", preStartTimer.toString())
                    players.forEach { p ->
                        p.sendActionBar(plugin.messageManager.parse(actionMsg))
                        if (preStartTimer in 1..3) {
                            val col = when (preStartTimer) { 1 -> "<red>"; 2 -> "<yellow>"; else -> "<green>" }
                            p.showTitle(Title.title(
                                plugin.messageManager.parse("$col<bold>$preStartTimer</bold>"),
                                Component.empty()
                            ))
                            p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f)
                        }
                    }
                    if (preStartTimer <= 0) {
                        phase = ParkourPhase.PLAYING
                        plugin.server.globalRegionScheduler.run(plugin) { _ ->
                            removeWall()
                            players.forEach { p ->
                                p.inventory.clear()
                                givePlayingItems(p)
                                p.showTitle(Title.title(
                                    plugin.messageManager.parse("<#39FF14><bold>¡GO!</bold></#39FF14>"),
                                    Component.empty()
                                ))
                                p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                                // Chain duos
                                if (isDuos) chainTeam(p)
                            }
                        }
                        iniciarTareaCheckpoints()
                    }
                }

                ParkourPhase.PLAYING -> {
                    gameTimer--
                    if (gameTimer <= 0) {
                        task.cancel()
                        plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                    }
                }

                ParkourPhase.ENDED -> task.cancel()
            }
        }, 20L, 20L)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Checkpoint detection task (runs every 5 ticks on main thread)
    // ─────────────────────────────────────────────────────────────────────────
    private fun iniciarTareaCheckpoints() {
        val halfSize = plugin.config.getDouble("parkour.checkpoint_radius", 1.0)
        runAtFixedRate( { task ->
            if (phase == ParkourPhase.ENDED || !isRunning) { task.cancel(); return@runAtFixedRate }
            if (phase != ParkourPhase.PLAYING) return@runAtFixedRate

            val maxWinners = plugin.config.getInt("parkour.max_winners", 5)

            for (p in players.toList()) {
                val tId = playerTeam[p] ?: continue
                if (finishedTeams.contains(tId)) continue

                val cpIdx = teamCheckpoint[tId] ?: 0
                if (cpIdx >= checkpoints.size) continue

                val cpLoc = checkpoints[cpIdx].clone().apply { world = p.world }
                val px = p.location.x
                val pz = p.location.z
                // 2×2 block area: player must be within halfSize blocks on X and Z
                val inArea = Math.abs(px - cpLoc.x) <= halfSize && Math.abs(pz - cpLoc.z) <= halfSize
                if (inArea) {
                    val next = cpIdx + 1
                    teamCheckpoint[tId] = next

                    val isLast = next >= checkpoints.size
                    if (isLast) {
                        finishTeam(tId)
                        if (finishedTeams.size >= maxWinners) {
                            task.cancel()
                            checkWinner(); return@runAtFixedRate
                        }
                    } else {
                        val cpMsg = plugin.languageManager.get("parkour.checkpoint_reached")
                            .ifBlank { "<#39FF14>✔ Checkpoint <b>%cp%/%total%</b> alcanzado!</#39FF14>" }
                            .replace("%cp%", next.toString())
                            .replace("%total%", checkpoints.size.toString())
                        teamMembers[tId]?.forEach { member ->
                            member.sendMessage(plugin.messageManager.parse(cpMsg))
                            member.playSound(member.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f)
                        }
                    }
                }
            }
        }, 5L, 5L)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Finish a team
    // ─────────────────────────────────────────────────────────────────────────
    fun finishTeam(tId: Int) {
        if (finishedTeams.contains(tId)) return
        finishedTeams.add(tId)
        val pos = finishedTeams.size

        val pointsList = plugin.config.getIntegerList("parkour.points")
        val pts = pointsList.getOrElse(pos - 1) { 0 }
        val names = teamMembers[tId]?.joinToString(" y ") { it.name } ?: "?"

        val broadMsg = plugin.languageManager.get("parkour.broadcast.finish")
            .ifBlank { "\n<#39FF14>🏁 <b>%player%</b></#39FF14> <white>terminó el parkour en <b>#%pos%</b> (+%pts% pts)\n" }
            .replace("%player%", names).replace("%pos%", pos.toString()).replace("%pts%", pts.toString())
        plugin.server.broadcast(plugin.messageManager.parse(broadMsg))

        teamMembers[tId]?.forEach { p ->
            p.playSound(p.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
            if (pts > 0) plugin.puntajeManager.addPoints(p, pts, "Top #$pos en Parkour")
            unchainPlayer(p)
            // Teleport to gradas/center
            val dest = currentArena?.gradas?.clone()?.apply { world = p.world }
                ?: currentArena?.centerLocation?.clone()?.apply { world = p.world }
            dest?.let { p.teleportAsync(it) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Solicitud de retroceder al checkpoint (líder)
    // ─────────────────────────────────────────────────────────────────────────
    fun requestCheckpointReturn(leader: Player) {
        val tId = playerTeam[leader] ?: return
        if (teamLeader[tId] != leader) {
            leader.sendMessage(plugin.messageManager.parse(
                plugin.languageManager.get("parkour.only_leader_can_request")
                    .ifBlank { "<red>Solo el líder puede solicitar retroceder.</red>" }
            ))
            return
        }
        val cpIdx = (teamCheckpoint[tId] ?: 1) - 1
        if (cpIdx < 0) { leader.sendMessage(plugin.messageManager.parse("<red>Ya estás en el primer checkpoint.</red>")); return }

        val teammates = (teamMembers[tId] ?: return).filter { it != leader }
        if (teammates.isEmpty()) { teleportTeamToCheckpoint(tId, cpIdx); return }

        teammates.forEach { tm ->
            pendingRequests[tm] = leader

            plugin.server.regionScheduler.run(plugin, tm.location) { _ ->
                val guiTitle = plugin.languageManager.get("parkour.menu.checkpoint_request_title")
                    .ifBlank { "§8Solicitud de Checkpoint" }
                val gui = Gui.gui().title(Component.text(guiTitle)).rows(1).disableAllInteractions().create()

                val accept = ItemBuilder.from(Material.LIME_CONCRETE)
                    .name(plugin.messageManager.parse(
                        plugin.languageManager.get("parkour.menu.checkpoint_accept")
                            .ifBlank { "<#39FF14><b>✔ Aceptar</b></#39FF14>" }
                    ))
                    .lore(plugin.messageManager.parse(
                        plugin.languageManager.get("parkour.menu.checkpoint_accept_lore")
                            .ifBlank { "<gray>${leader.name} quiere regresar al checkpoint.</gray>" }
                            .replace("%leader%", leader.name)
                    ))
                    .asGuiItem { _ ->
                        gui.close(tm)
                        pendingRequests.remove(tm)
                        teamCheckpoint[tId] = cpIdx
                        teleportTeamToCheckpoint(tId, cpIdx)
                    }

                val reject = ItemBuilder.from(Material.RED_CONCRETE)
                    .name(plugin.messageManager.parse(
                        plugin.languageManager.get("parkour.menu.checkpoint_reject")
                            .ifBlank { "<red><b>✖ Rechazar</b></red>" }
                    ))
                    .asGuiItem { _ ->
                        gui.close(tm)
                        pendingRequests.remove(tm)
                        tm.sendMessage(plugin.messageManager.parse(
                            plugin.languageManager.get("parkour.checkpoint_rejected")
                                .ifBlank { "<yellow>Rechazaste la solicitud.</yellow>" }
                        ))
                        leader.sendMessage(plugin.messageManager.parse(
                            plugin.languageManager.get("parkour.checkpoint_rejected_notify")
                                .ifBlank { "<yellow>${tm.name} rechazó tu solicitud.</yellow>" }
                                .replace("%player%", tm.name)
                        ))
                    }

                gui.setItem(3, accept)
                gui.setItem(5, reject)

                // Lore del título — leader.name quiere regresar
                tm.sendMessage(plugin.messageManager.parse(
                    plugin.languageManager.get("parkour.checkpoint_request_message")
                        .ifBlank { "<#39FF14>${leader.name} quiere regresar al checkpoint.</#39FF14> <gray>(revisa tu menú)</gray>" }
                        .replace("%leader%", leader.name)
                ))
                gui.open(tm)
            }
        }
    }

    private fun teleportTeamToCheckpoint(tId: Int, cpIdx: Int) {
        val cpLoc = checkpoints.getOrNull(cpIdx) ?: return
        teamMembers[tId]?.forEach { p ->
            val dest = cpLoc.clone().apply { world = p.world }
            p.teleportAsync(dest)
            p.sendMessage(plugin.messageManager.parse(
                plugin.languageManager.get("parkour.checkpoint_teleported")
                    .ifBlank { "<yellow>Regresaste al checkpoint %cp%.</yellow>" }
                    .replace("%cp%", (cpIdx + 1).toString())
            ))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Ocultar/mostrar jugadores toggle
    // ─────────────────────────────────────────────────────────────────────────
    fun toggleHidePlayers(p: Player) {
        if (hidingPlayers.contains(p)) {
            hidingPlayers.remove(p)
            players.forEach { other -> if (other != p) p.showPlayer(plugin, other) }
            p.sendMessage(plugin.messageManager.parse(
                plugin.languageManager.get("parkour.players_visible")
                    .ifBlank { "<green>Jugadores visibles.</green>" }
            ))
        } else {
            hidingPlayers.add(p)
            players.forEach { other -> if (other != p) p.hidePlayer(plugin, other) }
            p.sendMessage(plugin.messageManager.parse(
                plugin.languageManager.get("parkour.players_hidden")
                    .ifBlank { "<gray>Jugadores ocultos.</gray>" }
            ))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Chain API helpers (safe — no crash if plugin absent)
    // ─────────────────────────────────────────────────────────────────────────
    private fun chainTeam(p: Player) {
        if (!isDuos) return
        if (!plugin.config.getBoolean("parkour.chain.enabled", true)) return
        val tId = playerTeam[p] ?: return
        val leader = teamLeader[tId] ?: return
        if (p == leader) return
        runCatching {
            val chainClass = Class.forName("gg.lode.lodestone.chain.ChainModule")
            val getInstance = chainClass.getDeclaredMethod("getInstance")
            getInstance.isAccessible = true
            val instance = getInstance.invoke(null) ?: return

            val getManager = instance.javaClass.getMethod("getChainManager")
            val mgr = getManager.invoke(instance) ?: return

            val maxDist = plugin.config.getInt("parkour.chain.max_distance", 5)
            runCatching {
                val setMaxDist = mgr.javaClass.getMethod("setMaxDistance", Int::class.javaPrimitiveType)
                setMaxDist.invoke(mgr, maxDist)
            }

            val chainMethod = mgr.javaClass.getMethod("chain",
                org.bukkit.entity.Entity::class.java,
                org.bukkit.entity.Entity::class.java)
            chainMethod.invoke(mgr, leader, p)
        }.onFailure { runCatching { chainTeamFallback(leader, p) } }
    }

    /** Fallback: tries the older ChainAPI reflection path used in previous versions */
    private fun chainTeamFallback(leader: Player, follower: Player) {
        val api = Class.forName("gg.lode.chainapi.ChainAPI")
            .getDeclaredMethod("getApi").invoke(null) ?: return
        val mgr = api.javaClass.getMethod("getChainManager").invoke(api)
        val maxDist = plugin.config.getInt("parkour.chain.max_distance", 5)
        runCatching {
            api.javaClass.getMethod("setMaxDistance", Int::class.java).invoke(api, maxDist)
        }
        mgr.javaClass.getMethod("chain",
            org.bukkit.entity.Entity::class.java,
            org.bukkit.entity.Entity::class.java)
            .invoke(mgr, leader, follower)
    }

    fun unchainPlayer(p: Player) {
        runCatching {
            // Try new API first
            val chainClass = Class.forName("gg.lode.lodestone.chain.ChainModule")
            val getInstance = chainClass.getDeclaredMethod("getInstance")
            getInstance.isAccessible = true
            val instance = getInstance.invoke(null) ?: return
            val mgr = instance.javaClass.getMethod("getChainManager").invoke(instance) ?: return
            val isChained = mgr.javaClass.getMethod("isChained", org.bukkit.entity.Entity::class.java)
                .invoke(mgr, p) as? Boolean ?: false
            if (isChained) mgr.javaClass.getMethod("unchain", org.bukkit.entity.Entity::class.java)
                .invoke(mgr, p)
        }.onFailure {
            // Fallback to old ChainAPI
            runCatching {
                val api = Class.forName("gg.lode.chainapi.ChainAPI")
                    .getDeclaredMethod("getApi").invoke(null) ?: return
                val mgr = api.javaClass.getMethod("getChainManager").invoke(api)
                val isChained = mgr.javaClass.getMethod("isChained", org.bukkit.entity.Entity::class.java)
                    .invoke(mgr, p) as? Boolean ?: false
                if (isChained) mgr.javaClass.getMethod("unchain", org.bukkit.entity.Entity::class.java)
                    .invoke(mgr, p)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Muro de inicio (dueloA → dueloB, plano vertical completo)
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildWall(world: org.bukkit.World) {
        val a = currentArena?.dueloA ?: return
        val b = currentArena?.dueloB ?: return
        val matName = plugin.config.getString("parkour.wall_material", "GLASS") ?: "GLASS"
        val mat = runCatching { Material.valueOf(matName) }.getOrDefault(Material.GLASS)

        val minX = min(a.blockX, b.blockX); val maxX = max(a.blockX, b.blockX)
        val minY = min(a.blockY, b.blockY); val maxY = max(a.blockY, b.blockY)
        val minZ = min(a.blockZ, b.blockZ); val maxZ = max(a.blockZ, b.blockZ)

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            wallBlocks.clear()
            for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
                val block = world.getBlockAt(x, y, z)
                wallBlocks.add(Pair(block.location.clone(), block.blockData.clone()))
                block.setType(mat, false)
            }
        }
    }

    private fun removeWall() {
        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            wallBlocks.forEach { (loc, data) ->
                loc.world?.getBlockAt(loc)?.setBlockData(data, false)
            }
            wallBlocks.clear()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Items
    // ─────────────────────────────────────────────────────────────────────────
    private fun giveTeamSelectItem(p: Player) {
        p.inventory.setItem(4, org.bukkit.inventory.ItemStack(Material.BLAZE_POWDER).apply {
            itemMeta = itemMeta?.apply {
                displayName(plugin.messageManager.parse("<#FF5500><b>🚩 Seleccionar Equipo</b></#FF5500>"))
            }
        })
    }

    private fun givePlayingItems(p: Player) {
        val tId = playerTeam[p] ?: 0
        val isLeader = teamLeader[tId] == p

        // Ojo de ender para ocultar jugadores
        p.inventory.setItem(7, org.bukkit.inventory.ItemStack(Material.ENDER_EYE).apply {
            itemMeta = itemMeta?.apply {
                displayName(plugin.messageManager.parse(
                    plugin.languageManager.get("parkour.item.hide_players")
                        .ifBlank { "<gray>👁 Ocultar/Mostrar jugadores</gray>" }
                ))
            }
        })
        // Solo el líder en duos tiene el item de retroceder checkpoint
        if (isDuos && isLeader) {
            p.inventory.setItem(8, org.bukkit.inventory.ItemStack(Material.FEATHER).apply {
                itemMeta = itemMeta?.apply {
                    displayName(plugin.messageManager.parse(
                        plugin.languageManager.get("parkour.item.checkpoint_return")
                            .ifBlank { "<yellow>🔄 Pedir retroceder checkpoint</yellow>" }
                    ))
                }
            })
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Agrupación de equipos (dúos)
    // ─────────────────────────────────────────────────────────────────────────
    private fun agruparEquipos() {
        val firstTeamId = (playerTeam.values.maxOrNull() ?: 0) + 1
        TeamAssignmentService.pair(players.filterNot(playerTeam::containsKey), firstTeamId).forEach { assignment ->
            val leader = assignment.members.first()
            teamLeader[assignment.teamId] = leader
            teamMembers[assignment.teamId] = assignment.members.toMutableList()
            assignment.members.forEach { playerTeam[it] = assignment.teamId }
            if (assignment.members.size == 2) {
                val partner = assignment.members[1]
                plugin.languageManager.send(leader, "common.teams.teammate_assigned", Placeholder.unparsed("player", partner.name))
                plugin.languageManager.send(partner, "common.teams.teammate_assigned", Placeholder.unparsed("player", leader.name))
            }
            teamCheckpoint[assignment.teamId] = 0
        }
    }

    private fun teleportToSpawns(world: org.bukkit.World, spawns: List<Location>) {
        if (isDuos) {
            val teams = teamMembers.entries.toList()
            teams.forEachIndexed { i, (_, members) ->
                val loc = spawns.getOrElse(i % spawns.size.coerceAtLeast(1)) { spawns.firstOrNull() ?: return }
                    .clone().apply { this.world = world }
                members.forEach { p ->
                    p.teleportAsync(loc)
                    p.gameMode = GameMode.ADVENTURE
                    p.inventory.clear()
                }
            }
        } else {
            players.forEachIndexed { i, p ->
                val loc = spawns.getOrElse(i % spawns.size.coerceAtLeast(1)) { spawns.firstOrNull() ?: return }
                    .clone().apply { this.world = world }
                p.teleportAsync(loc)
                p.gameMode = GameMode.ADVENTURE
                p.inventory.clear()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  checkWinner / onStop
    // ─────────────────────────────────────────────────────────────────────────
    override fun checkWinner() {
        phase = ParkourPhase.ENDED

        // Quienes no terminaron → sin puntos, broadcast
        val remaining = teamMembers.keys.filter { !finishedTeams.contains(it) }
        remaining.forEach { tId ->
            teamMembers[tId]?.forEach { p -> unchainPlayer(p) }
        }

        if (finishedTeams.isEmpty()) {
            plugin.server.broadcast(plugin.messageManager.parse(
                plugin.languageManager.get("parkour.broadcast.no_winner")
                    .ifBlank { "\n<gray>Nadie terminó el parkour a tiempo.\n" }
            ))
        }
        stop()
    }

    override fun onStop() {
        phase = ParkourPhase.ENDED
        removeWall()

        players.forEach { p -> unchainPlayer(p) }
        returnToLobby()
    }
}

package pumpkin.eventos.games.battleroyale

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import pumpkin.eventos.games.support.TeamAssignmentService
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class BRPhase { TEAM_SELECT, PLAYING, ENDED }

class BattleRoyale(plugin: PumpkinEventos, val isDuos: Boolean)
    : EventGame(plugin,
        if (isDuos) "battleroyale_duos" else "battleroyale",
        if (isDuos) plugin.languageManager.get("battleroyale.display_duos")
                        .ifBlank { "<#FF3131>Battle Royale Dúos</#FF3131>" }
        else        plugin.languageManager.get("battleroyale.display_solo")
                        .ifBlank { "<#FF3131>Battle Royale</#FF3131>" }) {

    // ── Estado ────────────────────────────────────────────────────────────────
    var phase = BRPhase.TEAM_SELECT
    var mainTimer = 600

    // ── Equipos (dúos) ────────────────────────────────────────────────────────
    val playerTeam = mutableMapOf<Player, Int>()

    // ── Cofres ya abiertos ────────────────────────────────────────────────────
    val openedChests = mutableSetOf<Location>()
    val chestHolograms = mutableMapOf<Location, ArmorStand>()

    // ── Kills ────────────────────────────────────────────────────────────────
    val kills = mutableMapOf<Player, Int>()

    // ── Borde ─────────────────────────────────────────────────────────────────
    private var currentPhaseIdx = -1
    var borderDamage = 0.0

    // ── Refills ───────────────────────────────────────────────────────────────
    private var refill1Done = false
    private var refill2Done = false

    // ── Timer de selección de equipo ──────────────────────────────────────────
    private var teamSelectTimer = 15

    // ─────────────────────────────────────────────────────────────────────────
    //  Placeholders para scoreboard / bossbar
    // ─────────────────────────────────────────────────────────────────────────
    override fun getExtraPlaceholders(): Map<String, String> {
        val min = mainTimer / 60; val sec = mainTimer % 60
        val phaseLabel = if (currentPhaseIdx in BR_BORDER_PHASES.indices)
            BR_BORDER_PHASES[currentPhaseIdx].label else "Preparando..."

        val topKills = kills.entries.sortedByDescending { it.value }.take(3)
        val k1 = topKills.getOrNull(0)?.let { "${it.key.name} - ${it.value}" } ?: "Nadie - 0"
        val k2 = topKills.getOrNull(1)?.let { "${it.key.name} - ${it.value}" } ?: "Nadie - 0"
        val k3 = topKills.getOrNull(2)?.let { "${it.key.name} - ${it.value}" } ?: "Nadie - 0"

        return mapOf(
            "%br_timer%"  to String.format("%02d:%02d", min, sec),
            "%br_vivos%"  to players.size.toString(),
            "%br_fase%"   to phaseLabel,
            "%br_modo%"   to if (isDuos) "Dúos" else "Solo",
            "%br_k1%"     to k1,
            "%br_k2%"     to k2,
            "%br_k3%"     to k3
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> = mapOf(
        GameRule.FALL_DAMAGE     to true,
        GameRule.DO_MOB_SPAWNING to false,
        GameRule.KEEP_INVENTORY  to false
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  onStart
    // ─────────────────────────────────────────────────────────────────────────
    override fun onStart() {
        // Reiniciar estado
        openedChests.clear(); playerTeam.clear(); kills.clear()
        limpiarHologramas()
        currentPhaseIdx = -1; borderDamage = 0.0
        refill1Done = false; refill2Done = false
        mainTimer = plugin.config.getInt("battleroyale.timer", 600)
        teamSelectTimer = plugin.config.getInt("battleroyale.team_select_time", 15)

        players.forEach { kills[it] = 0 }

        val arena = currentArena ?: return
        val targetWorld = gameWorld ?: return

        // Inicializar borde grande
        val border = targetWorld.worldBorder
        border.center = arena.centerLocation ?: targetWorld.spawnLocation
        border.size = plugin.config.getDouble("battleroyale.border_initial", 500.0)
        border.damageBuffer = 0.0
        border.damageAmount = 0.0

        if (isDuos) {
            // ── DÚOS: fase selección de equipos ─────────────────────────────
            phase = BRPhase.TEAM_SELECT
            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                val center = arena.centerLocation?.clone()?.apply { world = targetWorld }
                    ?: targetWorld.spawnLocation
                players.forEach { p ->
                    p.teleportAsync(center)
                    p.gameMode = GameMode.ADVENTURE
                    p.inventory.clear()
                    // Item para abrir el menú de equipos
                    val teamItem = org.bukkit.inventory.ItemStack(Material.BLAZE_POWDER).apply {
                        itemMeta = itemMeta?.apply {
                            displayName(plugin.messageManager.parse(
                                "<#FF5500><b>🚩 Seleccionar Equipo</b></#FF5500>"
                            ))
                        }
                    }
                    p.inventory.setItem(4, teamItem)
                    p.sendMessage(plugin.messageManager.parse(
                        plugin.languageManager.get("battleroyale.team_select_hint")
                            .ifBlank { "<yellow>Tienes ${teamSelectTimer}s para elegir equipo.</yellow>" }
                            .replace("%time%", teamSelectTimer.toString())
                    ))
                }
            }, 10L)
        } else {
            // ── SOLO: empezar directamente ────────────────────────────────────
            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                iniciarPartida(arena.spawnPoints, targetWorld)
            }, 20L)
        }

        // ── Timer principal ───────────────────────────────────────────────────
        runAtFixedRate( { task ->

            when (phase) {

                BRPhase.TEAM_SELECT -> {
                    teamSelectTimer--
                    val actionMsg = plugin.languageManager.get("battleroyale.actionbar.team_select")
                        .ifBlank { "<yellow>Selección de equipos: <b>%time%s</b></yellow>" }
                        .replace("%time%", teamSelectTimer.toString())
                    players.forEach { it.sendActionBar(plugin.messageManager.parse(actionMsg)) }

                    if (teamSelectTimer <= 0) {
                        agruparEquipos()
                        plugin.server.globalRegionScheduler.run(plugin) { _ ->
                            iniciarPartida(arena.spawnPoints, targetWorld)
                        }
                    }
                }

                BRPhase.PLAYING -> {
                    mainTimer--

                    // ── Condición de victoria ─────────────────────────────────
                    if (checkWinCondition()) {
                        task.cancel()
                        plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                        return@runAtFixedRate
                    }

                    // ── Refills ───────────────────────────────────────────────
                    val r1 = plugin.config.getInt("battleroyale.refill1_at", 300)
                    val r2 = plugin.config.getInt("battleroyale.refill2_at", 120)

                    if (mainTimer == r1 && !refill1Done) {
                        refill1Done = true
                        openedChests.clear()
                        limpiarHologramas()
                        plugin.server.globalRegionScheduler.run(plugin) { _ ->
                            plugin.server.broadcast(plugin.messageManager.parse(
                                plugin.languageManager.get("battleroyale.broadcast.refill1")
                                    .ifBlank { "\n<#39FF14><b>♻ REFILL #1</b></#39FF14> <white>» ¡Los cofres han sido rellenados!\n" }
                            ))
                            players.forEach { it.playSound(it.location, Sound.BLOCK_CHEST_OPEN, 1f, 0.5f) }
                        }
                    }

                    if (mainTimer == r2 && !refill2Done) {
                        refill2Done = true
                        openedChests.clear()
                        limpiarHologramas()
                        plugin.server.globalRegionScheduler.run(plugin) { _ ->
                            plugin.server.broadcast(plugin.messageManager.parse(
                                plugin.languageManager.get("battleroyale.broadcast.refill2")
                                    .ifBlank { "\n<#FFCC00><b>♻ REFILL #2 (FINAL)</b></#FFCC00> <white>» ¡Última oportunidad!\n" }
                            ))
                            players.forEach { it.playSound(it.location, Sound.BLOCK_CHEST_OPEN, 1f, 0.8f) }
                        }
                    }

                    // ── Fases de borde ────────────────────────────────────────
                    BR_PHASE_TIMESTAMPS.forEachIndexed { idx, timestamp ->
                        if (mainTimer == timestamp && currentPhaseIdx < idx) {
                            currentPhaseIdx = idx
                            val bPhase = BR_BORDER_PHASES[idx]
                            borderDamage = bPhase.damagePerSecond
                            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                                val center = currentArena?.centerLocation ?: return@run
                                val world  = gameWorld ?: return@run
                                world.worldBorder.apply {
                                    this.center = center
                                    setSize(bPhase.targetSize, bPhase.shrinkSeconds)
                                }
                                val msg = plugin.languageManager.get("battleroyale.broadcast.border_shrink")
                                    .ifBlank { "\n<#FF3131><b>⚠ LA ZONA SE ESTÁ CERRANDO</b></#FF3131> <white>» %fase% — %size% bloques de radio.\n" }
                                    .replace("%fase%", bPhase.label)
                                    .replace("%size%", bPhase.targetSize.toInt().toString())
                                plugin.server.broadcast(plugin.messageManager.parse(msg))
                                players.forEach { p ->
                                    p.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 1f, 1f)
                                    p.showTitle(Title.title(
                                        plugin.messageManager.parse("<#FF3131><b>⚠ ZONA REDUCIÉNDOSE</b></#FF3131>"),
                                        plugin.messageManager.parse("<white>${bPhase.label} — ¡Métete al círculo!</white>")
                                    ))
                                }
                            }
                        }
                    }

                    // ── Daño fuera del borde ──────────────────────────────────
                    if (borderDamage > 0) {
                        plugin.server.globalRegionScheduler.run(plugin) { _ ->
                            val wb = gameWorld?.worldBorder ?: return@run
                            players.forEach { p ->
                                if (!wb.isInside(p.location)) {
                                    p.damage(borderDamage)
                                    val dmgMsg = plugin.languageManager.get("battleroyale.actionbar.outside_border")
                                        .ifBlank { "<#FF3131><b>⚠ ¡Estás fuera de la zona! (-%damage% ❤/s)</b></#FF3131>" }
                                        .replace("%damage%", borderDamage.toInt().toString())
                                    p.sendActionBar(plugin.messageManager.parse(dmgMsg))
                                }
                            }
                        }
                    }

                    if (mainTimer <= 0) {
                        task.cancel()
                        plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                    }
                }

                BRPhase.ENDED -> task.cancel()
            }
        }, 20L, 20L)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Agrupación automática de equipos sin asignar
    // ─────────────────────────────────────────────────────────────────────────
    private fun agruparEquipos() {
        val firstTeamId = (playerTeam.values.maxOrNull() ?: 0) + 1
        TeamAssignmentService.pair(players.filterNot(playerTeam::containsKey), firstTeamId).forEach { assignment ->
            assignment.members.forEach { playerTeam[it] = assignment.teamId }
            if (assignment.members.size == 2) {
                val (p1, p2) = assignment.members
                plugin.languageManager.send(p1, "common.teams.teammate_assigned", Placeholder.unparsed("player", p2.name))
                plugin.languageManager.send(p2, "common.teams.teammate_assigned", Placeholder.unparsed("player", p1.name))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Iniciar partida — teleportar a spawns, dar ceguera+lentitud, SIN jaulas
    // ─────────────────────────────────────────────────────────────────────────
    private fun iniciarPartida(spawns: List<Location>, world: org.bukkit.World) {
        phase = BRPhase.PLAYING

        // Repartir spawns (round-robin)
        val effectivePlayers = if (isDuos) {
            // Duos: un spawn por equipo
            players.groupBy { playerTeam[it] ?: 0 }.entries.toList()
                .let { entries ->
                    val result = mutableListOf<Pair<Player, Location>>()
                    entries.forEachIndexed { i, (_, members) ->
                        val loc = spawns.getOrElse(i % spawns.size.coerceAtLeast(1)) { spawns.first() }
                            .clone().apply { this.world = world }
                        members.forEach { result.add(Pair(it, loc)) }
                    }
                    result
                }
        } else {
            players.mapIndexed { i, p ->
                val loc = spawns.getOrElse(i % spawns.size.coerceAtLeast(1)) { spawns.first() }
                    .clone().apply { this.world = world }
                Pair(p, loc)
            }
        }

        effectivePlayers.forEach { (p, loc) ->
            p.teleportAsync(loc).thenAccept {
                p.gameMode = GameMode.SURVIVAL
                p.inventory.clear()
                p.health = p.maxHealth
                p.foodLevel = 20

                if (isDuos) {
                    plugin.server.regionScheduler.run(plugin, loc) { _ ->
                        p.isGlowing = true
                    }
                }

                // ── CEGUERA + LENTITUD II durante 5 segundos ──────────────────
                plugin.server.regionScheduler.run(plugin, loc) { _ ->
                    p.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS,  100, 0, false, false, false))
                    p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS,   100, 1, false, false, false))

                    p.showTitle(Title.title(
                        plugin.messageManager.parse("<#FF3131><b>¡BATTLE ROYALE!</b></#FF3131>"),
                        plugin.messageManager.parse("<white>¡Sobrevive y sé el último en pie!</white>")
                    ))
                    p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                }
            }
        }

        val startMsg = plugin.languageManager.get("battleroyale.broadcast.start")
            .ifBlank { "\n<#FF3131><b>⚔ BATTLE ROYALE HA COMENZADO</b></#FF3131> <white>» Modo: %modo%\n" }
            .replace("%modo%", if (isDuos) "Dúos" else "Solo")
            .replace("%tier%", "Normal")
        plugin.server.broadcast(plugin.messageManager.parse(startMsg))
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Hologramas sobre cofres vacíos
    // ─────────────────────────────────────────────────────────────────────────
    fun spawnHologramAtChest(loc: Location) {
        val holoLoc = loc.clone().add(0.5, 0.8, 0.5)
        plugin.server.regionScheduler.run(plugin, holoLoc) { _ ->
            val holo = holoLoc.world.spawnEntity(holoLoc, EntityType.ARMOR_STAND) as ArmorStand
            holo.isMarker = true; holo.isInvisible = true; holo.setGravity(false)
            holo.isCustomNameVisible = true
            holo.customName(plugin.messageManager.parse("<gray>☐ Vacío</gray>"))
            chestHolograms[loc] = holo
        }
    }

    private fun limpiarHologramas() {
        chestHolograms.values.forEach { if (!it.isDead) it.remove() }
        chestHolograms.clear()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Kill tracking
    // ─────────────────────────────────────────────────────────────────────────
    fun registerKill(killer: Player) {
        kills[killer] = (kills[killer] ?: 0) + 1
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Condición de victoria
    // ─────────────────────────────────────────────────────────────────────────
    private fun checkWinCondition(): Boolean {
        if (players.isEmpty()) return true
        if (!isDuos && players.size == 1) return true
        if (isDuos && players.mapNotNull { playerTeam[it] }.distinct().size <= 1) return true
        return false
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  checkWinner
    // ─────────────────────────────────────────────────────────────────────────
    override fun checkWinner() {
        phase = BRPhase.ENDED

        val winnerStr = if (isDuos) {
            val wTeam = playerTeam[players.firstOrNull() ?: run { stop(); return }]
            players.filter { playerTeam[it] == wTeam }.joinToString(" y ") { it.name }
        } else {
            players.firstOrNull()?.name ?: "Nadie"
        }

        val killerInfo = if (!isDuos) {
            val k = kills[players.firstOrNull()] ?: 0
            " con <green>$k kills</green>"
        } else ""

        val msg = plugin.languageManager.get("battleroyale.broadcast.winner")
            .ifBlank { "\n<#FF3131><b>BATTLE ROYALE</b></#FF3131> <white>» ¡<yellow>%player%</yellow> ha ganado%kills%!\n" }
            .replace("%player%", winnerStr)
            .replace("%kills%", killerInfo)
        plugin.server.broadcast(plugin.messageManager.parse(msg))

        players.forEach { p ->
            p.playSound(p.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
            p.world.spawnParticle(org.bukkit.Particle.FIREWORK, p.location, 100, 1.0, 1.0, 1.0)
            plugin.puntajeManager.addPoints(p, 15, "¡Victoria en Battle Royale!")
        }
        stop()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  onStop
    // ─────────────────────────────────────────────────────────────────────────
    override fun onStop() {
        phase = BRPhase.ENDED
        limpiarHologramas()

        gameWorld?.let { w ->
            w.worldBorder.size = 29999984.0
            w.worldBorder.damageAmount = 0.0
        }

        returnToLobby(beforeReset = { it.isGlowing = false })
    }
}

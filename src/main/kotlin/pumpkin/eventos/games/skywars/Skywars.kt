package pumpkin.eventos.games.skywars

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class SwPhase { TEAM_SELECT, VOTING, PLAYING, ENDED }

class Skywars(plugin: PumpkinEventos, val isDuos: Boolean) : EventGame(plugin, if (isDuos) "skywars_duos" else "skywars_solos", if (isDuos) "<#00FFFF>Skywars Duos</#00FFFF>" else "<#39FF14>Skywars Solos</#39FF14>") {

    var phase = SwPhase.TEAM_SELECT
    var mainTimer = 15

    // Votaciones
    val chestVotes = mutableMapOf<UUID, SkywarsChest>()
    val eventVotes = mutableMapOf<UUID, SkywarsEvent>()
    var chosenChest = SkywarsChest.NORMAL
    var chosenEvent = SkywarsEvent.BORDE

    // Equipos y Cofres
    val playerTeam = mutableMapOf<Player, Int>()
    val openedChests = mutableSetOf<Location>()
    val chestHolograms = mutableMapOf<Location, ArmorStand>() // Hologramas de los cofres
    val kills = mutableMapOf<Player, Int>() // Contador de asesinatos

    private val cages = mutableListOf<Location>()

    override fun getExtraPlaceholders(): Map<String, String> {
        val nextEvent = if (mainTimer > 300) "Refill" else chosenEvent.nombre
        val timeToEvent = if (mainTimer > 300) mainTimer - 300 else mainTimer
        val min = timeToEvent / 60
        val sec = timeToEvent % 60

        // Top Kills
        val topKills = kills.entries.sortedByDescending { it.value }.take(3)
        val k1 = topKills.getOrNull(0)?.let { "${it.key.name} - ${it.value}" } ?: "Nadie - 0"
        val k2 = topKills.getOrNull(1)?.let { "${it.key.name} - ${it.value}" } ?: "Nadie - 0"
        val k3 = topKills.getOrNull(2)?.let { "${it.key.name} - ${it.value}" } ?: "Nadie - 0"

        return mapOf(
            "%time%" to String.format("%02d:%02d", min, sec),
            "%fase%" to phase.name,
            "%vivos%" to players.size.toString(),
            "%modo%" to (if (isDuos) "Dúos" else "Solo"),
            "%proximo_evento%" to nextEvent,
            "%k1%" to k1, "%k2%" to k2, "%k3%" to k3
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return mapOf(GameRule.FALL_DAMAGE to true, GameRule.DO_MOB_SPAWNING to false)
    }

    override fun onStart() {
        chestVotes.clear(); eventVotes.clear(); openedChests.clear(); cages.clear(); playerTeam.clear(); kills.clear()
        limpiarHologramas()

        val arena = currentArena ?: return
        val targetWorld = gameWorld ?: return

        players.forEach { kills[it] = 0 } // Inicializar kills en 0

        if (isDuos) {
            phase = SwPhase.TEAM_SELECT
            mainTimer = 15
            plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
                players.forEach { p ->
                    p.gameMode = GameMode.ADVENTURE
                    p.inventory.clear()

                    val teamSelector = ItemStack(Material.BLAZE_POWDER)
                    val meta = teamSelector.itemMeta
                    meta.displayName(plugin.messageManager.parse("<#FF5500><b>🚩 Seleccionar Equipo</b></#FF5500>"))
                    teamSelector.itemMeta = meta
                    p.inventory.setItem(4, teamSelector)

                    p.sendMessage(plugin.messageManager.parse("<yellow>Tienes 15 segundos para elegir equipo.</yellow>"))
                }
            }, 10L)
        } else {
            iniciarVotacion(targetWorld, arena.spawnPoints)
        }

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            when (phase) {
                SwPhase.TEAM_SELECT -> {
                    mainTimer--
                    players.forEach { it.sendActionBar(plugin.messageManager.parse("<yellow>Selección de equipos: <b>$mainTimer</b>s</yellow>")) }
                    if (mainTimer <= 0) {
                        agruparEquipos()
                        iniciarVotacion(targetWorld, arena.spawnPoints)
                    }
                }
                SwPhase.VOTING -> {
                    mainTimer--
                    players.forEach { it.sendActionBar(plugin.messageManager.parse("<#00FFFF>Vota los eventos: <b>$mainTimer</b>s</#00FFFF>")) }
                    if (mainTimer <= 0) {
                        iniciarPartida()
                    }
                }
                SwPhase.PLAYING -> {
                    mainTimer--

                    val toEliminate = players.filter { it.location.y <= targetWorld.minHeight + 5 }
                    plugin.server.globalRegionScheduler.run(plugin) { _ -> toEliminate.forEach { eliminate(it) } }

                    if (checkWinCondition()) {
                        task.cancel()
                        plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
                        return@runAtFixedRate
                    }

                    // --- REFILL (MINUTO 5) ---
                    if (mainTimer == 300) {
                        openedChests.clear()
                        limpiarHologramas() // Borrar todos los letreros de "Vacío"
                        plugin.server.broadcast(plugin.messageManager.parse("<newline><#39FF14><b>¡LOS COFRES HAN SIDO RELLENADOS!</b></#39FF14><newline>"))
                        players.forEach { it.playSound(it.location, Sound.BLOCK_CHEST_OPEN, 1f, 0.5f) }
                    }

                    // Actualizar tiempo en los hologramas de los cofres vacíos
                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        val timeToRefill = if (mainTimer > 300) mainTimer - 300 else 0
                        val min = timeToRefill / 60
                        val sec = timeToRefill % 60
                        val timeStr = String.format("%02d:%02d", min, sec)

                        chestHolograms.values.forEach { holo ->
                            if (!holo.isDead) {
                                holo.customName(plugin.messageManager.parse("<red>¡Vacío!</red> <gray>|</gray> <aqua>Refill en $timeStr</aqua>"))
                            }
                        }
                    }

                    // --- EVENTO FINAL (MINUTO 10) ---
                    if (mainTimer == 0) {
                        SkywarsManager.ejecutarEventoFinal(chosenEvent, targetWorld, arena.centerLocation ?: targetWorld.spawnLocation, plugin)
                    }
                }
                SwPhase.ENDED -> task.cancel()
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun agruparEquipos() {
        val unassigned = players.filter { !playerTeam.containsKey(it) }.toMutableList()
        var teamIdCounter = playerTeam.values.maxOrNull() ?: 0

        while (unassigned.isNotEmpty()) {
            teamIdCounter++
            val p1 = unassigned.removeAt(0)
            playerTeam[p1] = teamIdCounter

            if (unassigned.isNotEmpty()) {
                val p2 = unassigned.removeAt(0)
                playerTeam[p2] = teamIdCounter
                p1.sendMessage(plugin.messageManager.parse("<green>Tu compañero es: <yellow>${p2.name}"))
                p2.sendMessage(plugin.messageManager.parse("<green>Tu compañero es: <yellow>${p1.name}"))
            }
        }
    }

    private fun iniciarVotacion(world: org.bukkit.World, spawns: List<Location>) {
        phase = SwPhase.VOTING
        mainTimer = 10

        val itemCofres = ItemStack(Material.CHEST).apply { itemMeta = itemMeta?.apply { displayName(plugin.messageManager.parse("<#39FF14><b>🗳 Votar Cofres</b>")) } }
        val itemEvento = ItemStack(Material.DRAGON_HEAD).apply { itemMeta = itemMeta?.apply { displayName(plugin.messageManager.parse("<#FF3131><b>🗳 Votar Evento Final</b>")) } }

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            if (isDuos) {
                val equipos = players.groupBy { playerTeam[it] ?: 0 }.values.toList()
                for (i in equipos.indices) {
                    val loc = spawns[i % spawns.size].clone().apply { this.world = world }
                    equipos[i].forEach { p ->
                        p.teleportAsync(loc)
                        p.inventory.clear()
                        p.inventory.setItem(3, itemCofres)
                        p.inventory.setItem(5, itemEvento)
                    }
                    crearJaula(loc)
                }
            } else {
                for (i in players.indices) {
                    val loc = spawns[i % spawns.size].clone().apply { this.world = world }
                    players[i].teleportAsync(loc)
                    players[i].inventory.clear()
                    players[i].inventory.setItem(3, itemCofres)
                    players[i].inventory.setItem(5, itemEvento)
                    crearJaula(loc)
                }
            }
        }
    }

    private fun crearJaula(loc: Location) {
        val cx = loc.blockX; val cy = loc.blockY; val cz = loc.blockZ
        val w = loc.world ?: return
        plugin.server.regionScheduler.run(plugin, loc) { _ ->
            for (x in -1..1) { for (y in -1..3) { for (z in -1..1) {
                if (x == -1 || x == 1 || z == -1 || z == 1 || y == -1 || y == 3) {
                    val b = w.getBlockAt(cx + x, cy + y, cz + z)
                    if (b.type.isAir) { b.setType(Material.GLASS, false); cages.add(b.location) }
                }
            }}}
        }
    }

    private fun iniciarPartida() {
        phase = SwPhase.PLAYING
        mainTimer = 600

        val chestTally = chestVotes.values.groupingBy { it }.eachCount()
        chosenChest = chestTally.maxByOrNull { it.value }?.key ?: SkywarsChest.NORMAL

        val eventTally = eventVotes.values.groupingBy { it }.eachCount()
        chosenEvent = eventTally.maxByOrNull { it.value }?.key ?: SkywarsEvent.BORDE

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            cages.forEach { loc -> loc.block.setType(Material.AIR, false) }
            cages.clear()

            plugin.server.broadcast(plugin.messageManager.parse("<newline><aqua><b>¡SKYWARS HA COMENZADO!</b></aqua>"))
            plugin.server.broadcast(plugin.messageManager.parse("<gray>Cofres: <white>${chosenChest.nombre} <gray>| Evento Final: <white>${chosenEvent.nombre}</gray><newline>"))

            players.forEach { p ->
                p.inventory.clear()
                p.gameMode = GameMode.SURVIVAL
                p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
            }
        }
    }

    fun spawnHologramAtChest(loc: Location) {
        val holoLoc = loc.clone().add(0.5, 0.8, 0.5) // Encima del cofre
        plugin.server.regionScheduler.run(plugin, holoLoc) { _ ->
            val holo = holoLoc.world.spawnEntity(holoLoc, EntityType.ARMOR_STAND) as ArmorStand
            holo.isMarker = true; holo.isInvisible = true; holo.setGravity(false)
            holo.isCustomNameVisible = true
            holo.customName(plugin.messageManager.parse("<red>¡Vacío!</red>"))
            chestHolograms[loc] = holo
        }
    }

    private fun limpiarHologramas() {
        chestHolograms.values.forEach { if (!it.isDead) it.remove() }
        chestHolograms.clear()
    }

    private fun checkWinCondition(): Boolean {
        if (players.isEmpty()) return true
        if (!isDuos && players.size == 1) return true

        if (isDuos) {
            val teamsVivos = players.mapNotNull { playerTeam[it] }.distinct()
            if (teamsVivos.size <= 1) return true
        }
        return false
    }

    override fun checkWinner() {
        phase = SwPhase.ENDED
        val winnerStr = if (isDuos) {
            val winnerTeamId = playerTeam[players.firstOrNull() ?: return]
            val ganadores = players.filter { playerTeam[it] == winnerTeamId }
            ganadores.joinToString(" y ") { it.name }
        } else {
            players.firstOrNull()?.name ?: "Nadie"
        }

        plugin.server.broadcast(plugin.messageManager.parse("<newline><#00FFFF><b>SKYWARS</b></#00FFFF> <white>» ¡<#39FF14>$winnerStr</#39FF14> ha ganado la partida!<newline>"))
        players.forEach {
            it.world.spawnParticle(org.bukkit.Particle.FIREWORK, it.location, 100)
            it.playSound(it.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
            plugin.puntajeManager.addPoints(it, 15, "¡Victoria en Skywars!")
        }
        stop()
    }

    override fun onStop() {
        phase = SwPhase.ENDED
        limpiarHologramas()
        plugin.eventManager.currentGame = null
        val lobby = plugin.arenaManager.mainLobby ?: plugin.server.worlds[0].spawnLocation
        (players + spectators).forEach { p ->
            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}

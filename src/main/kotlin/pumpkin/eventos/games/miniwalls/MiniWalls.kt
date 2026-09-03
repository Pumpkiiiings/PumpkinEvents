package pumpkin.eventos.games.miniwalls

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scoreboard.Team
import org.bukkit.util.Vector
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.BorderShrinkManager
import pumpkin.eventos.games.BoosterManager
import pumpkin.eventos.games.BoosterType
import pumpkin.eventos.games.EventGame
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

enum class MwPhase { LOBBY, PREPARING, PLAYING, DEATHMATCH, DRAW, ENDED }

enum class MwTeam(val displayName: String, val chatColor: String, val armorColor: Color, val wool: Material, val glass: Material) {
    RED("Rojo", "<red>", Color.RED, Material.RED_WOOL, Material.RED_STAINED_GLASS),
    BLUE("Azul", "<aqua>", Color.BLUE, Material.BLUE_WOOL, Material.BLUE_STAINED_GLASS),
    YELLOW("Amarillo", "<yellow>", Color.YELLOW, Material.YELLOW_WOOL, Material.YELLOW_STAINED_GLASS),
    GREEN("Verde", "<green>", Color.LIME, Material.GREEN_WOOL, Material.GREEN_STAINED_GLASS)
}

enum class MwKit(val displayName: String, val icon: Material, val maxArrows: Int, val arrowRegen: Int, val healOnKill: Double) {
    SOLDIER("<gray>Soldado</gray>", Material.IRON_SWORD, 2, 8, 6.0),
    ARCHER("<aqua>Arquero</aqua>", Material.BOW, 4, 4, 6.0),
    BUILDER("<yellow>Constructor</yellow>", Material.IRON_PICKAXE, 2, 8, 8.0),
    KNIGHT("<red>Caballero</red>", Material.DIAMOND_SWORD, 0, 0, 4.0),
    SCOUT("<green>Explorador</green>", Material.GOLDEN_BOOTS, 1, 6, 4.0)
}

class MiniWalls(plugin: PumpkinEventos) : EventGame(plugin, "miniwalls", "<gradient:#FF5500:#FF8800>Mini Walls</gradient>") {

    companion object {
        const val MAX_NEXUS_HP = 100
    }

    var phase = MwPhase.LOBBY
    var mainTimer = 20

    val playerTeam = mutableMapOf<Player, MwTeam>()
    val playerKit = mutableMapOf<Player, MwKit>()
    val respawnTimers = mutableMapOf<UUID, Int>()
    val teamNexusLoc = mutableMapOf<MwTeam, Location>()
    val teamNexusHP = mutableMapOf<MwTeam, Int>()
    val teamNexusCooldown = mutableMapOf<MwTeam, Long>()

    val teamSpawns = mutableMapOf<MwTeam, Location>()
    val placedBlocks = ConcurrentHashMap.newKeySet<Location>()
    var maxHeightLimit = 256

    private val arrowTicks = mutableMapOf<Player, Int>()
    private val boosterManager = BoosterManager(plugin, this)
    private val borderManager = BorderShrinkManager(plugin, this)
    private val mapBlocks = mutableListOf<Location>()

    private var nexoShootTimer = 0

    // Máximo de jugadores por equipo (0 = sin límite)
    val maxTeamSize = 0

    init {
        plugin.server.pluginManager.registerEvents(MiniWallsListener(plugin, this), plugin)
    }

    override fun getExtraPlaceholders(): Map<String, String> {
        val min = mainTimer / 60
        val sec = mainTimer % 60

        fun formatNexo(team: MwTeam): String {
            val hp = teamNexusHP[team]
            return if (hp == null || hp <= 0) "<red>✗</red>" else {
                val pct = (hp * 100) / MAX_NEXUS_HP
                when {
                    pct > 60 -> "<green>❤ $pct%</green>"
                    pct > 30 -> "<yellow>❤ $pct%</yellow>"
                    else     -> "<red>❤ $pct%</red>"
                }
            }
        }

        return mapOf(
            "%time%" to String.format("%02d:%02d", min, sec),
            "%phase%" to phase.name,
            "%w_red%" to formatNexo(MwTeam.RED),
            "%w_blue%" to formatNexo(MwTeam.BLUE),
            "%w_yellow%" to formatNexo(MwTeam.YELLOW),
            "%w_green%" to formatNexo(MwTeam.GREEN)
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return mapOf(
            GameRule.FALL_DAMAGE to true,
            GameRule.KEEP_INVENTORY to true,
            GameRule.NATURAL_REGENERATION to true,
            GameRule.DO_IMMEDIATE_RESPAWN to true,
            GameRule.MOB_GRIEFING to false
        )
    }

    override fun onStart() {
        phase = MwPhase.LOBBY
        mainTimer = 20
        nexoShootTimer = 0
        playerTeam.clear(); playerKit.clear(); teamNexusLoc.clear(); teamNexusHP.clear(); teamSpawns.clear()
        placedBlocks.clear(); arrowTicks.clear(); mapBlocks.clear(); teamNexusCooldown.clear()

        val arena = currentArena ?: return
        val targetWorld = gameWorld ?: return

        val lobbyWait = arena.dueloA?.clone()?.apply { world = targetWorld } ?: targetWorld.spawnLocation
        maxHeightLimit = arena.dueloB?.blockY ?: targetWorld.maxHeight

        val spawns = arena.spawnPoints
        MwTeam.values().forEachIndexed { index, team ->
            if (index < spawns.size) {
                teamSpawns[team] = spawns[index].clone().apply { world = targetWorld }
            }
        }

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            players.forEach { p ->
                p.gameMode = GameMode.ADVENTURE
                p.inventory.clear()
                p.teleportAsync(lobbyWait)

                val currentTeam = playerTeam[p]
                val woolMat = currentTeam?.wool ?: Material.WHITE_WOOL
                val teamItem = ItemStack(woolMat).apply {
                    itemMeta = itemMeta?.apply {
                        displayName(plugin.messageManager.parse("<aqua><b>🚩 Elegir Equipo</b></aqua>"))
                    }
                }
                p.inventory.setItem(0, teamItem)
            }
            iniciarRelojMaestro()
        }, 10L)
    }

    private fun iniciarRelojMaestro() {
        val targetWorld = gameWorld ?: return
        var tickNexo = 0

        runAtFixedRate( { task ->

            if (phase == MwPhase.PLAYING) {
                tickNexo++
                nexoAura()
                if (tickNexo >= 3) {
                    tickNexo = 0
                    nexoDispara()
                }
            }

            when (phase) {
                MwPhase.LOBBY -> {
                    players.forEach { it.sendActionBar(plugin.messageManager.parse("<yellow>El juego inicia en: <b>${mainTimer}s</b></yellow>")) }
                    if (mainTimer <= 0) {
                        agruparJugadoresYKits()
                        iniciarPreparacion(targetWorld)
                    }
                    mainTimer--
                }
                MwPhase.PREPARING -> {
                    players.forEach { it.sendActionBar(plugin.messageManager.parse("<green>Tiempo de Preparación: <b>${mainTimer}s</b></green>")) }
                    if (mainTimer <= 0) {
                        caerParedes(targetWorld)
                        phase = MwPhase.PLAYING
                        mainTimer = 300
                        val startMsg = plugin.messageManager.parse("<bold><color:#FF5500>¡LAS PAREDES HAN CAÍDO! ¡A LUCHAR!</color></bold>")
                        plugin.server.broadcast(startMsg)
                        players.forEach { it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f) }
                        boosterManager.start(listOf(BoosterType.STRENGTH_1, BoosterType.REGEN_1, BoosterType.GAPPLE))
                    }
                    mainTimer--
                }
                MwPhase.PLAYING -> {
                    regenerarFlechas()
                    if (checkWinCondition()) { task.cancel(); return@runAtFixedRate }

                    if (mainTimer <= 0) iniciarDeathmatch()
                    mainTimer--
                }
                MwPhase.DEATHMATCH -> {
                    regenerarFlechas()
                    if (checkWinCondition()) { task.cancel(); return@runAtFixedRate }
                    romperMapaProgresivo(targetWorld)

                    if (mainTimer <= 0) {
                        task.cancel()
                        plugin.server.globalRegionScheduler.run(plugin) { _ -> declararEmpate() }
                        return@runAtFixedRate
                    }
                    mainTimer--
                }
                MwPhase.DRAW, MwPhase.ENDED -> task.cancel()
            }
        }, 20L, 20L)
    }

    private fun nexoAura() {
        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            teamNexusLoc.forEach { (equipo, loc) ->
                if ((teamNexusHP[equipo] ?: 0) > 0) {
                    val radio = 6.0
                    players.forEach { p ->
                        if (p.location.world == loc.world && p.location.distance(loc) <= radio) {
                            if (playerTeam[p] == equipo) {
                                p.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false, false))
                                p.world.spawnParticle(Particle.HAPPY_VILLAGER, p.location.add(0.0, 1.0, 0.0), 3, 0.2, 0.2, 0.2)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun nexoDispara() {
        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            teamNexusLoc.forEach { (equipo, nexoLoc) ->
                if ((teamNexusHP[equipo] ?: 0) <= 0) return@forEach

                val objetivo = players.firstOrNull {
                    it.location.world == nexoLoc.world &&
                            it.location.distance(nexoLoc) <= 10.0 &&
                            playerTeam[it] != equipo
                }

                if (objetivo != null) {
                    val origenLaser = nexoLoc.clone().add(0.5, 1.5, 0.5)
                    val dir = objetivo.eyeLocation.subtract(origenLaser).toVector()

                    for (i in 1..10) {
                        val point = origenLaser.clone().add(dir.clone().multiply(i / 10.0))
                        nexoLoc.world.spawnParticle(Particle.FLAME, point, 2, 0.0, 0.0, 0.0, 0.0)
                    }

                    nexoLoc.world.playSound(nexoLoc, Sound.ENTITY_GUARDIAN_ATTACK, 1f, 1f)
                    objetivo.damage(4.0)
                    objetivo.addPotionEffect(PotionEffect(PotionEffectType.POISON, 60, 0))
                    objetivo.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 1))
                }
            }
        }
    }

    private fun agruparJugadoresYKits() {
        val unassigned = players.filter { !playerTeam.containsKey(it) }.toMutableList()
        val availableTeams = teamSpawns.keys.toList()

        var tIndex = 0
        while (unassigned.isNotEmpty()) {
            playerTeam[unassigned.removeAt(0)] = availableTeams[tIndex % availableTeams.size]
            tIndex++
        }

        players.forEach { p ->
            if (!playerKit.containsKey(p)) playerKit[p] = MwKit.values().random()
        }
    }

    private fun iniciarPreparacion(world: org.bukkit.World) {
        phase = MwPhase.PREPARING
        mainTimer = 30

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            val sb = Bukkit.getScoreboardManager().mainScoreboard

            teamSpawns.forEach { (equipo, spawn) ->
                val sbTeam = sb.getTeam("MW_${equipo.name}") ?: sb.registerNewTeam("MW_${equipo.name}")
                sbTeam.color(net.kyori.adventure.text.format.NamedTextColor.NAMES.value(equipo.name.lowercase()) ?: net.kyori.adventure.text.format.NamedTextColor.WHITE)
                sbTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS)
                sbTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)
                sbTeam.setAllowFriendlyFire(false)

                players.filter { playerTeam[it] == equipo }.forEach { p ->
                    sbTeam.addEntry(p.name)
                    p.teleportAsync(spawn)
                    equipPlayer(p)
                }

                val nexoLoc = spawn.clone().add(0.0, 1.0, 0.0)
                teamNexusLoc[equipo] = nexoLoc
                teamNexusHP[equipo] = MAX_NEXUS_HP
                teamNexusCooldown[equipo] = 0L

                plugin.server.regionScheduler.run(plugin, nexoLoc) { _ ->
                    nexoLoc.clone().subtract(0.0, 1.0, 0.0).block.setType(equipo.wool, false)
                    nexoLoc.block.setType(Material.BEACON, false)
                    nexoLoc.clone().add(0.0, 1.0, 0.0).block.setType(equipo.glass, false)
                    world.spawnParticle(Particle.END_ROD, nexoLoc.clone().add(0.5, 1.5, 0.5), 50, 0.5, 0.5, 0.5, 0.1)
                }
            }
        }
    }

    private fun caerParedes(world: org.bukkit.World) {
        val bloquesPared = plugin.config.getStringList("miniwalls.wall_blocks").mapNotNull { Material.matchMaterial(it) }
        if (bloquesPared.isEmpty()) return

        val arena = currentArena ?: return
        val allPoints = mutableListOf<Location>()
        arena.centerLocation?.let { allPoints.add(it) }
        allPoints.addAll(arena.spawnPoints)
        if (allPoints.isEmpty()) return

        val minX = allPoints.minOf { it.blockX } - 50; val maxX = allPoints.maxOf { it.blockX } + 50
        val minZ = allPoints.minOf { it.blockZ } - 50; val maxZ = allPoints.maxOf { it.blockZ } + 50
        val avgY = allPoints.sumOf { it.blockY } / allPoints.size
        val minY = max(world.minHeight, avgY - 30); val maxY = min(maxHeightLimit, avgY + 30)

        for (cx in (minX shr 4)..(maxX shr 4)) {
            for (cz in (minZ shr 4)..(maxZ shr 4)) {
                plugin.server.regionScheduler.run(plugin, world, cx, cz) { _ ->
                    for (x in max(minX, cx shl 4)..min(maxX, (cx shl 4) + 15)) {
                        for (z in max(minZ, cz shl 4)..min(maxZ, (cz shl 4) + 15)) {
                            for (y in minY..maxY) {
                                val block = world.getBlockAt(x, y, z)
                                if (bloquesPared.contains(block.type)) {
                                    block.setType(Material.AIR, false)
                                    world.spawnParticle(Particle.CLOUD, block.location.add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0.05)
                                } else if (block.type.isSolid) {
                                    mapBlocks.add(block.location)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun iniciarDeathmatch() {
        phase = MwPhase.DEATHMATCH
        mainTimer = 180
        plugin.server.broadcast(plugin.messageManager.parse("<newline><bold><color:#BF00FF>¡DEATHMATCH!</color></bold> <white>¡Todos los Nexos han sido destruidos! El mapa colapsará en 3 minutos.</white><newline>"))

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            teamNexusLoc.forEach { (equipo, loc) ->
                teamNexusHP[equipo] = 0
                plugin.server.regionScheduler.run(plugin, loc) { _ ->
                    loc.block.setType(Material.AIR)
                    loc.clone().add(0.0, 1.0, 0.0).block.setType(Material.AIR)
                }
            }
            players.forEach { it.playSound(it.location, Sound.ENTITY_WITHER_DEATH, 1f, 1f) }
            mapBlocks.shuffle()
        }

        val targetWorld = gameWorld ?: return
        val center = currentArena?.centerLocation
        val cx = center?.x ?: 0.0
        val cz = center?.z ?: 0.0
        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            borderManager.start(targetWorld, cx, cz, delaySeconds = 0L)
        }
    }

    private fun romperMapaProgresivo(world: org.bukkit.World) {
        if (mapBlocks.isEmpty()) return

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            val aRomper = min(15, mapBlocks.size)
            for (i in 0 until aRomper) {
                val loc = mapBlocks.removeAt(mapBlocks.lastIndex)
                plugin.server.regionScheduler.run(plugin, loc) { _ ->
                    val block = loc.block
                    if (!block.type.isAir) {
                        block.setType(Material.AIR, false)
                        world.spawnParticle(Particle.BLOCK, loc.clone().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, Material.STONE.createBlockData())
                        if (Math.random() < 0.1) world.playSound(loc, Sound.BLOCK_STONE_BREAK, 0.5f, 0.8f)
                    }
                }
            }
        }
    }

    private fun regenerarFlechas() {
        players.forEach { p ->
            val kit = playerKit[p] ?: return@forEach
            if (kit.maxArrows > 0 && kit.arrowRegen > 0) {
                val ticks = (arrowTicks[p] ?: 0) + 1
                if (ticks >= kit.arrowRegen) {
                    arrowTicks[p] = 0
                    var flechasActuales = 0
                    p.inventory.contents.forEach { if (it?.type == Material.ARROW) flechasActuales += it.amount }

                    if (flechasActuales < kit.maxArrows) {
                        p.inventory.addItem(ItemStack(Material.ARROW))
                    }
                } else {
                    arrowTicks[p] = ticks
                }
            }
        }
    }

    fun equipPlayer(p: Player) {
        val team = playerTeam[p] ?: return
        val kit = playerKit[p] ?: return

        p.inventory.clear()
        p.gameMode = GameMode.SURVIVAL
        p.health = p.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        p.foodLevel = 20

        val helmet = ItemStack(Material.LEATHER_HELMET).apply { itemMeta = (itemMeta as LeatherArmorMeta).apply { setColor(team.armorColor); isUnbreakable = true } }
        val chest = ItemStack(Material.LEATHER_CHESTPLATE).apply { itemMeta = (itemMeta as LeatherArmorMeta).apply { setColor(team.armorColor); isUnbreakable = true } }
        val legs = ItemStack(Material.LEATHER_LEGGINGS).apply { itemMeta = (itemMeta as LeatherArmorMeta).apply { setColor(team.armorColor); isUnbreakable = true } }
        val boots = ItemStack(Material.LEATHER_BOOTS).apply { itemMeta = (itemMeta as LeatherArmorMeta).apply { setColor(team.armorColor); isUnbreakable = true } }

        p.inventory.helmet = helmet; p.inventory.chestplate = chest; p.inventory.leggings = legs; p.inventory.boots = boots

        when (kit) {
            MwKit.SOLDIER -> {
                p.inventory.addItem(ItemStack(Material.STONE_SWORD).apply { itemMeta = itemMeta?.apply { isUnbreakable = true } })
                p.inventory.addItem(ItemStack(Material.STONE_PICKAXE).apply { itemMeta = itemMeta?.apply { addEnchant(Enchantment.EFFICIENCY, 3, true) } })
                p.inventory.addItem(ItemStack(Material.WOODEN_AXE))
                p.inventory.addItem(ItemStack(team.wool, 10))
                p.inventory.addItem(ItemStack(Material.WOODEN_SHOVEL))
            }
            MwKit.ARCHER -> {
                p.inventory.addItem(ItemStack(Material.WOODEN_SWORD).apply { itemMeta = itemMeta?.apply { isUnbreakable = true } })
                p.inventory.addItem(ItemStack(Material.BOW).apply { itemMeta = itemMeta?.apply { isUnbreakable = true } })
                p.inventory.addItem(ItemStack(Material.STONE_PICKAXE).apply { itemMeta = itemMeta?.apply { addEnchant(Enchantment.EFFICIENCY, 3, true) } })
                p.inventory.addItem(ItemStack(Material.WOODEN_AXE))
                p.inventory.addItem(ItemStack(team.wool, 10))
            }
            MwKit.BUILDER -> {
                p.inventory.addItem(ItemStack(Material.WOODEN_SWORD).apply { itemMeta = itemMeta?.apply { isUnbreakable = true } })
                p.inventory.addItem(ItemStack(Material.IRON_PICKAXE).apply { itemMeta = itemMeta?.apply { addEnchant(Enchantment.EFFICIENCY, 3, true) } })
                p.inventory.addItem(ItemStack(Material.STONE_AXE))
                p.inventory.addItem(ItemStack(team.wool, 20))
                p.inventory.addItem(ItemStack(Material.STONE_SHOVEL))
            }
            MwKit.KNIGHT -> {
                p.inventory.chestplate = ItemStack(Material.IRON_CHESTPLATE)
                p.inventory.addItem(ItemStack(Material.IRON_SWORD).apply { itemMeta = itemMeta?.apply { isUnbreakable = true } })
                p.inventory.addItem(ItemStack(Material.WOODEN_PICKAXE).apply { itemMeta = itemMeta?.apply { addEnchant(Enchantment.EFFICIENCY, 3, true) } })
                p.inventory.addItem(ItemStack(team.wool, 10))
            }
            MwKit.SCOUT -> {
                val knockSword = ItemStack(Material.STONE_SWORD).apply { itemMeta = itemMeta?.apply { isUnbreakable = true; addEnchant(Enchantment.KNOCKBACK, 1, true) } }
                p.inventory.addItem(knockSword)
                p.inventory.addItem(ItemStack(Material.GOLDEN_PICKAXE).apply { itemMeta = itemMeta?.apply { addEnchant(Enchantment.EFFICIENCY, 3, true) } })
                p.inventory.addItem(ItemStack(team.wool, 15))
                p.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 99999, 0, false, false))
            }
        }
    }

    override fun eliminate(player: Player) {
        if (!players.contains(player)) return

        val team = playerTeam[player] ?: return
        val hpNexo = teamNexusHP[team] ?: 0

        if (hpNexo > 0) {
            val spawn = teamSpawns[team] ?: return
            val respawnSeconds = 5

            player.gameMode = GameMode.SPECTATOR
            player.inventory.clear()
            player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.5f)

            var secondsLeft = respawnSeconds
            runAtFixedRate( { task ->
                if (!isRunning || !players.contains(player)) {
                    task.cancel()
                    return@runAtFixedRate
                }

                if (secondsLeft <= 0) {
                    task.cancel()
                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        player.teleportAsync(spawn).thenRun {
                            plugin.server.regionScheduler.runDelayed(plugin, spawn, { _ ->
                                if (isRunning && players.contains(player)) {
                                    equipPlayer(player)
                                }
                            }, 5L)
                        }
                    }
                    return@runAtFixedRate
                }

                val title = net.kyori.adventure.title.Title.title(
                    plugin.messageManager.parse("<red><b>¡MUERTO!</b></red>"),
                    plugin.messageManager.parse("<white>Reaparecerás en <yellow><b>$secondsLeft</b></yellow> segundo${if (secondsLeft == 1) "" else "s"}</white>"),
                    net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ZERO,
                        java.time.Duration.ofMillis(1100),
                        java.time.Duration.ZERO
                    )
                )
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    player.showTitle(title)
                    player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, if (secondsLeft <= 2) 1.5f else 1f)
                }

                secondsLeft--
            }, 1L, 20L)
        } else {
            players.remove(player)
            spectators.add(player)
            player.gameMode = GameMode.SPECTATOR
            player.inventory.clear()
            player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 2f)

            plugin.server.broadcast(plugin.messageManager.parse("<red>☠</red> <yellow>${player.name}</yellow> <white>ha sido eliminado definitivamente.</white>"))

            if (checkWinCondition()) {
                plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
            }
        }
    }

    private fun checkWinCondition(): Boolean {
        if (phase == MwPhase.LOBBY || phase == MwPhase.PREPARING) return false
        val teamsVivos = players.mapNotNull { playerTeam[it] }.distinct()
        return teamsVivos.size <= 1
    }

    override fun checkWinner() {
        phase = MwPhase.ENDED
        val winnerTeam = players.mapNotNull { playerTeam[it] }.distinct().firstOrNull()

        val winnerStr = winnerTeam?.displayName ?: "Nadie"
        val color = winnerTeam?.chatColor ?: "<white>"

        val winMsg = plugin.languageManager.get("miniwalls.broadcast.winner")
            .replace("<team_color>", color)
            .replace("<team_name>", winnerStr)
            .replace("</team_color>", "</${color.removePrefix("<")}")
        plugin.server.broadcast(plugin.messageManager.parse(winMsg))

        players.filter { playerTeam[it] == winnerTeam }.forEach {
            it.world.spawnParticle(Particle.FIREWORK, it.location, 100)
            it.playSound(it.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
            plugin.puntajeManager.addPoints(it, 15, "¡Victoria en Mini Walls!")
        }
        stop()
    }

    fun declararEmpate() {
        phase = MwPhase.DRAW
        plugin.server.broadcast(plugin.messageManager.parse("<newline><gold><bold>¡EMPATE!</bold></gold> <white>El tiempo se agotó. Nadie ganó esta partida.</white><newline>"))
        val drawTitle = net.kyori.adventure.title.Title.title(
            plugin.messageManager.parse("<gold><b>¡EMPATE!</b></gold>"),
            plugin.messageManager.parse("<white>El tiempo se agotó</white>")
        )
        players.forEach { p ->
            p.showTitle(drawTitle)
            p.playSound(p.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.5f)
            plugin.puntajeManager.addPoints(p, 5, "Empate en Mini Walls")
        }
        stop()
    }

    override fun onStop() {
        phase = MwPhase.ENDED
        boosterManager.stop()
        gameWorld?.let { borderManager.stop(it) }

        val sb = Bukkit.getScoreboardManager().mainScoreboard
        MwTeam.values().forEach { sb.getTeam("MW_${it.name}")?.unregister() }
        returnToLobby()
    }
}

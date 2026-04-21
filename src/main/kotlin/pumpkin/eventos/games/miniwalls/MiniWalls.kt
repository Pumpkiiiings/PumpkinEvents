package pumpkin.eventos.games.miniwalls

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
import org.bukkit.attribute.Attribute
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Wither
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scoreboard.Team
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.BoosterManager
import pumpkin.eventos.games.BoosterType
import pumpkin.eventos.games.EventGame
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

enum class MwPhase { LOBBY, PREPARING, PLAYING, DEATHMATCH, ENDED }

enum class MwTeam(val displayName: String, val chatColor: String, val armorColor: Color, val wool: Material) {
    RED("Rojo", "<#FF3131>", Color.RED, Material.RED_WOOL),
    BLUE("Azul", "<#00FFFF>", Color.BLUE, Material.BLUE_WOOL),
    YELLOW("Amarillo", "<#FFD700>", Color.YELLOW, Material.YELLOW_WOOL),
    GREEN("Verde", "<#39FF14>", Color.LIME, Material.GREEN_WOOL)
}

enum class MwKit(val displayName: String, val icon: Material, val maxArrows: Int, val arrowRegen: Int, val healOnKill: Double) {
    SOLDIER("<gray>Soldado</gray>", Material.IRON_SWORD, 2, 8, 6.0),
    ARCHER("<#00FFFF>Arquero</#00FFFF>", Material.BOW, 4, 4, 6.0),
    BUILDER("<#FFD700>Constructor</#FFD700>", Material.IRON_PICKAXE, 2, 8, 8.0),
    KNIGHT("<#FF3131>Caballero</#FF3131>", Material.DIAMOND_SWORD, 0, 0, 4.0),
    SCOUT("<#39FF14>Explorador</#39FF14>", Material.GOLDEN_BOOTS, 1, 6, 4.0)
}

class MiniWalls(plugin: PumpkinEventos) : EventGame(plugin, "miniwalls", "<#FF5500>Mini Walls</#FF5500>") {

    var phase = MwPhase.LOBBY
    var mainTimer = 20

    val playerTeam = mutableMapOf<Player, MwTeam>()
    val playerKit = mutableMapOf<Player, MwKit>()
    val teamWithers = mutableMapOf<MwTeam, Wither>()
    val teamSpawns = mutableMapOf<MwTeam, Location>()

    // Antigrief: Solo pueden romper lo que han puesto
    val placedBlocks = ConcurrentHashMap.newKeySet<Location>()

    // Limite de construcción
    var maxHeightLimit = 256

    private val arrowTicks = mutableMapOf<Player, Int>()
    private val boosterManager = BoosterManager(plugin, this)

    // Para la destrucción del mapa
    private val mapBlocks = mutableListOf<Location>()

    init {
        plugin.server.pluginManager.registerEvents(MiniWallsListener(plugin, this), plugin)
    }

    override fun getExtraPlaceholders(): Map<String, String> {
        val min = mainTimer / 60
        val sec = mainTimer % 60

        fun formatWither(team: MwTeam): String {
            val w = teamWithers[team]
            if (w == null || w.isDead) return "<red>✗</red>"
            return "<green>❤ ${w.health.toInt()}</green>"
        }

        return mapOf(
            "%time%" to String.format("%02d:%02d", min, sec),
            "%phase%" to phase.name,
            "%w_red%" to formatWither(MwTeam.RED),
            "%w_blue%" to formatWither(MwTeam.BLUE),
            "%w_yellow%" to formatWither(MwTeam.YELLOW),
            "%w_green%" to formatWither(MwTeam.GREEN)
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return mapOf(
            GameRule.FALL_DAMAGE to true,
            GameRule.KEEP_INVENTORY to true,
            GameRule.NATURAL_REGENERATION to true,
            GameRule.DO_IMMEDIATE_RESPAWN to true
        )
    }

    override fun onStart() {
        phase = MwPhase.LOBBY
        mainTimer = 20
        playerTeam.clear(); playerKit.clear(); teamWithers.clear(); teamSpawns.clear()
        placedBlocks.clear(); arrowTicks.clear(); mapBlocks.clear()

        val arena = currentArena ?: return
        val targetWorld = gameWorld ?: return

        // El Duelo A es el Lobby de Espera del mapa
        val lobbyWait = arena.dueloA?.clone()?.apply { world = targetWorld } ?: targetWorld.spawnLocation
        // El Duelo B marca la altura máxima (Max Y)
        maxHeightLimit = arena.dueloB?.blockY ?: targetWorld.maxHeight

        // Configurar los 4 spawns (Orden: Rojo, Azul, Amarillo, Verde)
        val spawns = arena.spawnPoints
        MwTeam.values().forEachIndexed { index, team ->
            if (index < spawns.size) {
                teamSpawns[team] = spawns[index].clone().apply { world = targetWorld }
            }
        }

        // --- FASE 1: LOBBY (20 SEGUNDOS) ---
        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            players.forEach { p ->
                p.gameMode = GameMode.ADVENTURE
                p.inventory.clear()
                p.teleportAsync(lobbyWait)

                // Items de selección
                val teamItem = ItemStack(Material.WHITE_WOOL).apply { itemMeta = itemMeta?.apply { displayName(plugin.messageManager.parse("<#00FFFF><b>🚩 Elegir Equipo</b></#00FFFF>")) } }
                p.inventory.setItem(0, teamItem)
            }
            iniciarRelojMaestro()
        }, 10L)
    }

    private fun iniciarRelojMaestro() {
        val arena = currentArena ?: return
        val targetWorld = gameWorld ?: return

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            when (phase) {
                MwPhase.LOBBY -> {
                    players.forEach { it.sendActionBar(plugin.messageManager.parse("<yellow>El juego inicia en: <b>$mainTimer</b>s</yellow>")) }
                    if (mainTimer <= 0) {
                        agruparJugadoresYKits()
                        iniciarPreparacion(targetWorld)
                    }
                    mainTimer--
                }
                MwPhase.PREPARING -> {
                    players.forEach { it.sendActionBar(plugin.messageManager.parse("<#39FF14>Tiempo de Preparación: <b>$mainTimer</b>s</#39FF14>")) }
                    if (mainTimer <= 0) {
                        caerParedes(targetWorld)
                        phase = MwPhase.PLAYING
                        mainTimer = 300 // 5 Minutos de juego normal

                        val startMsg = plugin.messageManager.parse("<#FF5500><bold>¡LAS PAREDES HAN CAÍDO! ¡A LUCHAR!</bold></#FF5500>")
                        plugin.server.broadcast(startMsg)
                        players.forEach { it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f) }

                        boosterManager.start(listOf(BoosterType.STRENGTH_1, BoosterType.REGEN_1, BoosterType.GAPPLE))
                    }
                    mainTimer--
                }
                MwPhase.PLAYING -> {
                    regenerarFlechas()
                    if (checkWinCondition()) { task.cancel(); return@runAtFixedRate }

                    if (mainTimer <= 0) {
                        iniciarDeathmatch()
                    }
                    mainTimer--
                }
                MwPhase.DEATHMATCH -> {
                    regenerarFlechas()
                    if (checkWinCondition()) { task.cancel(); return@runAtFixedRate }

                    // Cada segundo se rompen 10 bloques del mapa
                    romperMapaProgresivo(targetWorld)
                }
                MwPhase.ENDED -> task.cancel()
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun agruparJugadoresYKits() {
        // Asignar equipos faltantes
        val unassigned = players.filter { !playerTeam.containsKey(it) }.toMutableList()
        val availableTeams = teamSpawns.keys.toList()

        var tIndex = 0
        while (unassigned.isNotEmpty()) {
            playerTeam[unassigned.removeAt(0)] = availableTeams[tIndex % availableTeams.size]
            tIndex++
        }

        // Asignar kits aleatorios si no eligieron (Como pediste, aleatorio en esta fase)
        players.forEach { p ->
            if (!playerKit.containsKey(p)) {
                playerKit[p] = MwKit.values().random()
            }
        }
    }

    private fun iniciarPreparacion(world: org.bukkit.World) {
        phase = MwPhase.PREPARING
        mainTimer = 30 // 30s de Prep

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            // Spawneamos Withers y configuramos Scoreboard Teams para que no ataquen a los suyos
            val sb = Bukkit.getScoreboardManager().mainScoreboard

            teamSpawns.forEach { (equipo, spawn) ->
                val sbTeam = sb.getTeam("MW_${equipo.name}") ?: sb.registerNewTeam("MW_${equipo.name}")
                sbTeam.color(net.kyori.adventure.text.format.NamedTextColor.NAMES.value(equipo.name.lowercase()) ?: net.kyori.adventure.text.format.NamedTextColor.WHITE)
                sbTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS)
                sbTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)

                // Mover a los jugadores a su base
                players.filter { playerTeam[it] == equipo }.forEach { p ->
                    sbTeam.addEntry(p.name)
                    p.teleportAsync(spawn)
                    equipPlayer(p)
                }

                // Spawn Wither
                val witherLoc = spawn.clone().add(0.0, 1.0, 0.0)
                plugin.server.regionScheduler.run(plugin, witherLoc) { _ ->
                    val wither = world.spawnEntity(witherLoc, EntityType.WITHER) as Wither
                    wither.isCustomNameVisible = true
                    wither.customName(plugin.messageManager.parse("${equipo.chatColor}<b>NEXO ${equipo.displayName.uppercase()}</b>"))

                    // Quitar IA de movimiento pero dejar ataques
                    wither.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 999999, 255, false, false))
                    wither.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 100.0 // 100 HP para el Wither
                    wither.health = 100.0

                    sbTeam.addEntry(wither.uniqueId.toString()) // Aliados del Wither
                    teamWithers[equipo] = wither
                }
            }
        }
    }

    private fun caerParedes(world: org.bukkit.World) {
        // Leemos la config para saber qué bloques romper
        val bloquesPared = plugin.config.getStringList("miniwalls.wall_blocks").mapNotNull { Material.matchMaterial(it) }
        if (bloquesPared.isEmpty()) return

        // Escanear el mapa y convertir a aire
        val arena = currentArena ?: return
        val posA = arena.dueloA ?: return
        val posB = arena.dueloB ?: return

        val minX = min(posA.blockX, posB.blockX); val maxX = max(posA.blockX, posB.blockX)
        val minZ = min(posA.blockZ, posB.blockZ); val maxZ = max(posA.blockZ, posB.blockZ)
        val minY = world.minHeight; val maxY = posB.blockY

        val minCX = minX shr 4; val maxCX = maxX shr 4
        val minCZ = minZ shr 4; val maxCZ = maxZ shr 4

        for (cx in minCX..maxCX) {
            for (cz in minCZ..maxCZ) {
                plugin.server.regionScheduler.run(plugin, world, cx, cz) { _ ->
                    for (x in max(minX, cx shl 4)..min(maxX, (cx shl 4) + 15)) {
                        for (z in max(minZ, cz shl 4)..min(maxZ, (cz shl 4) + 15)) {
                            for (y in minY..maxY) {
                                val block = world.getBlockAt(x, y, z)
                                if (bloquesPared.contains(block.type)) {
                                    block.setType(Material.AIR, false)
                                    world.spawnParticle(Particle.CLOUD, block.location.add(0.5,0.5,0.5), 2, 0.2, 0.2, 0.2, 0.05)
                                } else if (block.type.isSolid) {
                                    mapBlocks.add(block.location) // Guardamos bloques sólidos para el Deathmatch
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
        plugin.server.broadcast(plugin.messageManager.parse("<newline><#BF00FF><bold>¡DEATHMATCH!</bold></#BF00FF> <white>¡Todos los Withers han sido destruidos! El mapa colapsará.</white><newline>"))

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            // Matar Withers restantes
            teamWithers.values.forEach { w ->
                if (!w.isDead) w.health = 0.0
            }
            teamWithers.clear()

            players.forEach { it.playSound(it.location, Sound.ENTITY_WITHER_DEATH, 1f, 1f) }
            mapBlocks.shuffle() // Desordenamos los bloques del mapa para romperlos al azar
        }
    }

    private fun romperMapaProgresivo(world: org.bukkit.World) {
        if (mapBlocks.isEmpty()) return

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            // Romper 15 bloques al azar por segundo
            val aRomper = min(15, mapBlocks.size)
            for (i in 0 until aRomper) {
                val loc = mapBlocks.removeAt(mapBlocks.lastIndex)
                plugin.server.regionScheduler.run(plugin, loc) { _ ->
                    val block = loc.block
                    if (!block.type.isAir) {
                        block.setType(Material.AIR, false)
                        world.spawnParticle(Particle.BLOCK, loc.clone().add(0.5,0.5,0.5), 5, 0.2, 0.2, 0.2, Material.STONE.createBlockData())
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

        // Armadura de Equipo
        val helmet = ItemStack(Material.LEATHER_HELMET).apply { itemMeta = (itemMeta as LeatherArmorMeta).apply { setColor(team.armorColor); isUnbreakable = true } }
        val chest = ItemStack(Material.LEATHER_CHESTPLATE).apply { itemMeta = (itemMeta as LeatherArmorMeta).apply { setColor(team.armorColor); isUnbreakable = true } }
        val legs = ItemStack(Material.LEATHER_LEGGINGS).apply { itemMeta = (itemMeta as LeatherArmorMeta).apply { setColor(team.armorColor); isUnbreakable = true } }
        val boots = ItemStack(Material.LEATHER_BOOTS).apply { itemMeta = (itemMeta as LeatherArmorMeta).apply { setColor(team.armorColor); isUnbreakable = true } }

        p.inventory.helmet = helmet; p.inventory.chestplate = chest; p.inventory.leggings = legs; p.inventory.boots = boots

        // Items por Kit
        when (kit) {
            MwKit.SOLDIER -> {
                p.inventory.addItem(ItemStack(Material.STONE_SWORD).apply { itemMeta = itemMeta?.apply { isUnbreakable = true } })
                p.inventory.addItem(ItemStack(Material.STONE_PICKAXE))
                p.inventory.addItem(ItemStack(Material.WOODEN_AXE))
                p.inventory.addItem(ItemStack(team.wool, 10))
                p.inventory.addItem(ItemStack(Material.WOODEN_SHOVEL))
            }
            MwKit.ARCHER -> {
                p.inventory.addItem(ItemStack(Material.WOODEN_SWORD).apply { itemMeta = itemMeta?.apply { isUnbreakable = true } })
                p.inventory.addItem(ItemStack(Material.BOW).apply { itemMeta = itemMeta?.apply { isUnbreakable = true } })
                p.inventory.addItem(ItemStack(Material.STONE_PICKAXE))
                p.inventory.addItem(ItemStack(Material.WOODEN_AXE))
                p.inventory.addItem(ItemStack(team.wool, 10))
            }
            MwKit.BUILDER -> {
                p.inventory.addItem(ItemStack(Material.WOODEN_SWORD).apply { itemMeta = itemMeta?.apply { isUnbreakable = true } })
                p.inventory.addItem(ItemStack(Material.IRON_PICKAXE))
                p.inventory.addItem(ItemStack(Material.STONE_AXE))
                p.inventory.addItem(ItemStack(team.wool, 20))
                p.inventory.addItem(ItemStack(Material.STONE_SHOVEL))
            }
            MwKit.KNIGHT -> {
                p.inventory.chestplate = ItemStack(Material.IRON_CHESTPLATE) // Pechera de hierro
                p.inventory.addItem(ItemStack(Material.IRON_SWORD).apply { itemMeta = itemMeta?.apply { isUnbreakable = true } })
                p.inventory.addItem(ItemStack(Material.WOODEN_PICKAXE))
                p.inventory.addItem(ItemStack(team.wool, 10))
            }
            MwKit.SCOUT -> {
                val knockSword = ItemStack(Material.STONE_SWORD).apply { itemMeta = itemMeta?.apply { isUnbreakable = true; addEnchant(Enchantment.KNOCKBACK, 1, true) } }
                p.inventory.addItem(knockSword)
                p.inventory.addItem(ItemStack(Material.GOLDEN_PICKAXE))
                p.inventory.addItem(ItemStack(team.wool, 15))
                p.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 99999, 0, false, false))
            }
        }
    }

    override fun eliminate(player: Player) {
        if (!players.contains(player)) return

        val team = playerTeam[player] ?: return
        val wither = teamWithers[team]

        if (wither != null && !wither.isDead) {
            // WITHER VIVO: Respawnea
            player.sendActionBar(plugin.messageManager.parse("<green>¡Reapareciendo en tu base!</green>"))
            val spawn = teamSpawns[team]!!
            player.teleportAsync(spawn).thenRun {
                plugin.server.regionScheduler.runDelayed(plugin, spawn, { _ -> equipPlayer(player) }, 5L)
            }
        } else {
            // WITHER MUERTO: Eliminación real
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

        plugin.server.broadcast(plugin.messageManager.parse("<newline><#FF5500><b>MINI WALLS</b></#FF5500> <white>» ¡El equipo $color$winnerStr</$color> ha ganado la partida!<newline>"))

        players.filter { playerTeam[it] == winnerTeam }.forEach {
            it.world.spawnParticle(Particle.FIREWORK, it.location, 100)
            it.playSound(it.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
            plugin.puntajeManager.addPoints(it, 15, "¡Victoria en Mini Walls!")
        }
        stop()
    }

    override fun onStop() {
        phase = MwPhase.ENDED
        boosterManager.stop()

        // Limpiar Scoreboard Teams
        val sb = Bukkit.getScoreboardManager().mainScoreboard
        MwTeam.values().forEach { sb.getTeam("MW_${it.name}")?.unregister() }

        plugin.eventManager.currentGame = null
        val lobby = plugin.arenaManager.mainLobby ?: plugin.server.worlds[0].spawnLocation
        (players + spectators).forEach { p ->
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}

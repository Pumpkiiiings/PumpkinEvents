package pumpkin.eventos.games.corona

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.scoreboard.Team
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.BoosterManager
import pumpkin.eventos.games.BoosterType
import pumpkin.eventos.games.EventGame
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

class RobaLaCorona(plugin: PumpkinEventos) : EventGame(plugin, "corona", "<#FFD700>Roba la Corona</#FFD700>"), Listener {

    var currentHolder: Player? = null
    private val scores = mutableMapOf<UUID, Int>()
    private var gameTimer = 180

    private var teamCorona: Team? = null
    private var teamPlebeyos: Team? = null

    private val boosterManager = BoosterManager(plugin, this)

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun getExtraPlaceholders(): Map<String, String> {
        val min = gameTimer / 60
        val sec = gameTimer % 60
        return mapOf(
            "%time_left%" to String.format("%02d:%02d", min, sec),
            "%holder%" to (currentHolder?.name ?: "Nadie"),
            "%puntos%" to (currentHolder?.let { scores[it.uniqueId] }?.toString() ?: "0")
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return mapOf(
            GameRule.FALL_DAMAGE to false,
            GameRule.KEEP_INVENTORY to true
        )
    }

    override fun onStart() {
        gameTimer = 180
        scores.clear()
        setupTeams()

        val arena = currentArena ?: return
        val targetWorld = gameWorld ?: players.firstOrNull()?.world ?: return
        val spawn = arena.centerLocation?.clone() ?: arena.spawnPoints.firstOrNull()?.clone() ?: return
        spawn.world = targetWorld

        players.forEach { p ->
            p.gameMode = GameMode.SURVIVAL
            p.inventory.clear()
            p.health = 20.0
            scores[p.uniqueId] = 0
            p.teleportAsync(spawn)

            teamPlebeyos?.addEntry(p.name)
            p.isGlowing = true
        }

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            val primerRey = players.randomOrNull()
            if (primerRey != null) {
                asignarCorona(primerRey)
                plugin.server.broadcast(plugin.messageManager.parse("<newline><#FFD700>👑 ¡<b>${primerRey.name}</b> ha comenzado con la corona!</bold></#FFD700><newline>"))
            }

            // Iniciar boosters
            boosterManager.start(listOf(
                BoosterType.SPEED_1,
                BoosterType.SPEED_2,
                BoosterType.JUMP_1,
                BoosterType.MIX,
                BoosterType.PEARL
            ))

            iniciarReloj()
        }, 100L)
    }

    private fun setupTeams() {
        val sb = Bukkit.getScoreboardManager().mainScoreboard
        teamCorona = sb.getTeam("Corona") ?: sb.registerNewTeam("Corona")
        teamPlebeyos = sb.getTeam("Plebeyos") ?: sb.registerNewTeam("Plebeyos")

        teamCorona?.color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
        teamPlebeyos?.color(net.kyori.adventure.text.format.NamedTextColor.WHITE)

        teamCorona?.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)
        teamPlebeyos?.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)
    }

    private fun asignarCorona(p: Player) {
        currentHolder?.let {
            it.inventory.helmet = null
            teamPlebeyos?.addEntry(it.name)
        }

        currentHolder = p
        teamCorona?.addEntry(p.name)

        val corona = ItemStack(Material.GOLDEN_HELMET)
        val meta = corona.itemMeta
        if (meta != null) {
            meta.displayName(plugin.messageManager.parse("<#FFD700><b>👑 CORONA REAL 👑</b>"))
            meta.addEnchant(Enchantment.PROTECTION, 10, true)
            meta.isUnbreakable = true
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES)
            corona.itemMeta = meta
        }

        p.inventory.helmet = corona
        p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        p.sendTitle("§6§l👑 ERES EL REY", "§f¡Suma puntos huyendo!", 5, 40, 10)
    }

    private fun iniciarReloj() {
        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            gameTimer--

            currentHolder?.let { holder ->
                val currentScore = scores[holder.uniqueId] ?: 0
                scores[holder.uniqueId] = currentScore + 5
                holder.sendActionBar(plugin.messageManager.parse("<#FFD700>👑 <b>¡Rey Actual!</b> <gray>|</gray> +5 Puntos"))
            }

            if (gameTimer <= 0) {
                task.cancel()
                plugin.server.globalRegionScheduler.run(plugin) { _ -> checkWinner() }
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    @EventHandler
    fun alGolpear(e: EntityDamageByEntityEvent) {
        if (!isRunning) return
        val atacante = e.damager as? Player ?: return
        val victima = e.entity as? Player ?: return

        if (victima == currentHolder && players.contains(atacante)) {
            e.damage = 0.001
            asignarCorona(atacante)
            plugin.server.broadcast(plugin.messageManager.parse("<#FFD700>👑 <b>${atacante.name}</b> <white>le robó la corona a <gray>${victima.name}"))
            victima.playSound(victima.location, Sound.ENTITY_ITEM_BREAK, 1f, 1f)
        }
    }

    @EventHandler
    fun alMoverInventario(e: InventoryClickEvent) {
        if (!isRunning) return

        if (e.slot == 39 || e.rawSlot == 5 || e.slotType == InventoryType.SlotType.ARMOR) {
            e.isCancelled = true
            return
        }

        if (e.click.isShiftClick && e.currentItem?.type == Material.GOLDEN_HELMET) {
            e.isCancelled = true
        }
    }
    @EventHandler
    fun alTirarCorona(e: PlayerDropItemEvent) {
        if (isRunning && e.itemDrop.itemStack.type == Material.GOLDEN_HELMET) {
            e.isCancelled = true
            e.player.sendMessage(plugin.messageManager.parse("<red>¡La corona está pegada a tu cabeza!</red>"))
        }
    }

    override fun checkWinner() {
        val winnerEntry = scores.maxByOrNull { it.value }
        val winner = winnerEntry?.key?.let { Bukkit.getPlayer(it) }

        if (winner != null) {
            plugin.server.broadcast(plugin.messageManager.parse("<newline><#FFD700>👑 <b>ROBA LA CORONA</b></#FFD700> <white>» ¡<#39FF14>${winner.name}</#39FF14> ganó con <yellow>${winnerEntry.value}</yellow> puntos!<newline>"))
            winner.world.spawnParticle(org.bukkit.Particle.FIREWORK, winner.location, 100)
            plugin.puntajeManager.addPoints(winner, 15, "Rey Supremo")
        }
        stop()
    }

    override fun onStop() {
        boosterManager.stop()
        currentHolder = null

        // Intentar limpiar equipos
        try {
            teamCorona?.unregister()
            teamPlebeyos?.unregister()
        } catch (ignored: Exception) {}

        plugin.eventManager.currentGame = null
        var lobby = plugin.arenaManager.mainLobby ?: plugin.server.worlds[0].spawnLocation

        (players + spectators).forEach { p ->
            p.isGlowing = false
            p.inventory.clear()
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}

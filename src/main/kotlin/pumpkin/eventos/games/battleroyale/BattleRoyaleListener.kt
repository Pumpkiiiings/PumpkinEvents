package pumpkin.eventos.games.battleroyale

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Fireball
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import pumpkin.eventos.PumpkinEventos

class BattleRoyaleListener(private val plugin: PumpkinEventos) : Listener {

    // ── Kill tracking (igual que SkywarsListener) ─────────────────────────────
    private val lastDamager = mutableMapOf<Player, Pair<Player, Long>>()
    private val muertesProcesadas = mutableSetOf<Player>()

    // ─────────────────────────────────────────────────────────────────────────
    //  Rastreo del último atacante
    // ─────────────────────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamageByEntity(e: EntityDamageByEntityEvent) {
        val victim = e.entity as? Player ?: return
        val game = getGame() ?: return
        if (!game.players.contains(victim)) return

        var damager: Player? = null
        when (val d = e.damager) {
            is Player     -> damager = d
            is Projectile -> if (d.shooter is Player) damager = d.shooter as Player
            is TNTPrimed  -> if (d.source  is Player) damager = d.source  as Player
            is Fireball   -> if (d.shooter is Player) damager = d.shooter as Player
        }

        if (damager != null && damager != victim && game.players.contains(damager)) {
            lastDamager[victim] = Pair(damager, System.currentTimeMillis())
        }
    }

    private fun procesarKill(victim: Player, game: BattleRoyale) {
        if (muertesProcesadas.contains(victim)) return
        muertesProcesadas.add(victim)

        var killer = victim.killer
        if (killer == null) {
            val record = lastDamager[victim]
            if (record != null && System.currentTimeMillis() - record.second < 15_000) {
                killer = record.first
            }
        }

        if (killer != null && game.players.contains(killer) && killer != victim) {
            game.registerKill(killer)
            killer.sendMessage(plugin.messageManager.parse(
                plugin.languageManager.get("battleroyale.kill_message")
                    .ifBlank { "<#FF3131>⚔ <b>¡Eliminaste a <yellow>${victim.name}</yellow>! (+1 kill)</b></#FF3131>" }
                    .replace("%victim%", victim.name)
            ))
        }
        lastDamager.remove(victim)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDeath(e: PlayerDeathEvent) {
        val victim = e.entity
        val game = getGame() ?: return
        if (game.isRunning && game.players.contains(victim)) procesarKill(victim, game)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMoveToVoid(e: PlayerMoveEvent) {
        if (e.to.y > e.to.world.minHeight + 8.0) return
        val p = e.player
        val game = getGame() ?: return
        if (game.phase != BRPhase.PLAYING) {
            lastDamager.clear(); muertesProcesadas.clear(); return
        }
        if (!game.players.contains(p)) return
        if (e.to.y <= e.to.world.minHeight + 6.0) procesarKill(p, game)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Interacciones: cofres → loot | Blaze Powder → menú equipo (sólo dúos)
    // ─────────────────────────────────────────────────────────────────────────
    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        val game = getGame() ?: return
        if (!game.isRunning) return

        // 1. Cofre → generar loot al abrirlo por primera vez
        if (e.action == Action.RIGHT_CLICK_BLOCK) {
            val block = e.clickedBlock ?: return
            if (block.type == Material.CHEST || block.type == Material.TRAPPED_CHEST) {
                if (game.phase == BRPhase.PLAYING && !game.openedChests.contains(block.location)) {
                    game.openedChests.add(block.location)

                    val state = block.state as? org.bukkit.block.Chest ?: return
                    state.inventory.clear()
                    val loot = BRLootManager.generarLoot()
                    loot.forEach { item ->
                        val slot = (0 until 27).random()
                        state.inventory.setItem(slot, item)
                    }
                    game.spawnHologramAtChest(block.location)
                }
            }
        }

        // 2. Blaze Powder → menú de selección de equipo (solo en TEAM_SELECT)
        if (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK) {
            val item = e.item ?: return
            if (item.type == Material.BLAZE_POWDER && game.phase == BRPhase.TEAM_SELECT && game.isDuos) {
                e.isCancelled = true
                abrirMenuEquipos(p, game)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Romper cofre → soltar loot si nunca fue abierto + limpiar holograma
    // ─────────────────────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onChestBreak(e: BlockBreakEvent) {
        val game = getGame() ?: return
        if (!game.isRunning || game.phase != BRPhase.PLAYING) return

        val block = e.block
        if (block.type == Material.CHEST || block.type == Material.TRAPPED_CHEST) {
            val loc = block.location

            // Borrar holograma si existe
            game.chestHolograms[loc]?.let { holo ->
                if (!holo.isDead) holo.remove()
                game.chestHolograms.remove(loc)
            }

            // Soltar loot si el cofre nunca fue abierto
            if (!game.openedChests.contains(loc)) {
                game.openedChests.add(loc)
                BRLootManager.generarLoot().forEach { item ->
                    block.world.dropItemNaturally(loc, item)
                }
            }
        }
    }

    // Cofre colocado por jugador → marcar como ya abierto (evita re-loot)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onChestPlace(e: BlockPlaceEvent) {
        val game = getGame() ?: return
        if (!game.isRunning || game.phase != BRPhase.PLAYING) return
        val block = e.blockPlaced
        if (block.type == Material.CHEST || block.type == Material.TRAPPED_CHEST) {
            game.openedChests.add(block.location)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Daño — no friendly fire en dúos
    // ─────────────────────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(e: EntityDamageEvent) {
        val p = e.entity as? Player ?: return
        val game = getGame() ?: return
        if (!game.isRunning || !game.players.contains(p)) return

        // Sin fuego amigo en dúos
        if (game.isDuos && e is EntityDamageByEntityEvent) {
            val damager = when (val d = e.damager) {
                is Player     -> d
                is Projectile -> d.shooter as? Player
                else          -> null
            }
            if (damager != null && game.playerTeam[p] == game.playerTeam[damager]) {
                e.isCancelled = true
                return
            }
        }

        // Inmunidad de caída los primeros 5 s (100 ticks) desde el inicio de PLAYING
        if (e.cause == EntityDamageEvent.DamageCause.FALL
            && game.phase == BRPhase.PLAYING
            && game.mainTimer >= plugin.config.getInt("battleroyale.timer", 600) - 5) {
            e.isCancelled = true
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Menú de selección de equipo (solo dúos / TEAM_SELECT)
    // ─────────────────────────────────────────────────────────────────────────
    private fun abrirMenuEquipos(p: Player, game: BattleRoyale) {
        val menu = plugin.menuManager
        val gui = Gui.gui().title(plugin.messageManager.parse(menu.text("team-selection", "title", "<dark_gray>Seleccionar equipo")))
            .rows(menu.number("team-selection", "rows", 3).coerceIn(1, 6)).disableAllInteractions().create()
        val maxEquipos = (game.players.size / 2) + 1

        for (i in 1..maxEquipos) {
            val teamPlayers = game.players.filter { game.playerTeam[it] == i }
            val isFull = teamPlayers.size >= 2
            val icon = menu.material("team-selection", if (isFull) "team.full-material" else "team.available-material", if (isFull) Material.RED_STAINED_GLASS_PANE else Material.GREEN_STAINED_GLASS_PANE)
            val status = if (isFull)
                plugin.languageManager.get("battleroyale.menu.team_full").ifBlank { "<red>Lleno</red>" }
            else
                plugin.languageManager.get("battleroyale.menu.team_join").ifBlank { "<green>Clic para unirte</green>" }

            val lore = mutableListOf(status, "")
            teamPlayers.forEach { lore.add("<gray>- ${it.name}") }

            val item = ItemBuilder.from(icon)
                .name(plugin.messageManager.parse("<yellow>Equipo $i</yellow>"))
                .lore(lore.map { plugin.messageManager.parse(it) })
                .asGuiItem { _ ->
                    if (!isFull) {
                        game.playerTeam[p] = i
                        p.sendMessage(plugin.messageManager.parse(
                            plugin.languageManager.get("battleroyale.team_joined")
                                .ifBlank { "<green>Te has unido al Equipo $i.</green>" }
                                .replace("%team%", i.toString())
                        ))
                        p.playSound(p.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                        gui.close(p)
                    }
                }
            gui.addItem(item)
        }
        gui.open(p)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper
    // ─────────────────────────────────────────────────────────────────────────
    private fun getGame(): BattleRoyale? = plugin.eventManager.currentGame as? BattleRoyale
}

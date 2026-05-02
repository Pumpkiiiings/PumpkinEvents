package pumpkin.eventos.games.skywars

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

class SkywarsListener(private val plugin: PumpkinEventos) : Listener {

    // MAPAS PARA TRACKEAR KILLS Y DAÑO
    private val lastDamager = mutableMapOf<Player, Pair<Player, Long>>()
    private val muertesProcesadas = mutableSetOf<Player>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    // --- ⚔️ SISTEMA AVANZADO DE KILLS Y RASTREO ⚔️ ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamageByEntity(e: EntityDamageByEntityEvent) {
        val victim = e.entity as? Player ?: return
        val game = plugin.eventManager.currentGame as? Skywars ?: return
        if (!game.isRunning || !game.players.contains(victim)) return

        var damager: Player? = null

        // Extraer al atacante real incluso si usó proyectiles o explosivos
        when (val d = e.damager) {
            is Player -> damager = d
            is Projectile -> {
                if (d.shooter is Player) damager = d.shooter as Player
            }
            is TNTPrimed -> {
                if (d.source is Player) damager = d.source as Player
            }
            is Fireball -> {
                if (d.shooter is Player) damager = d.shooter as Player
            }
        }

        // Si fue dañado por un jugador válido, guardamos el momento exacto
        if (damager != null && damager != victim && game.players.contains(damager)) {
            lastDamager[victim] = Pair(damager, System.currentTimeMillis())
        }
    }

    private fun procesarKill(victim: Player, game: Skywars) {
        // Evitar sumar la kill dos veces si el evento salta repetidamente
        if (muertesProcesadas.contains(victim)) return
        muertesProcesadas.add(victim)

        var killer = victim.killer

        // Si Bukkit no sabe quién lo mató (vacío, caída, fireball), usamos nuestro tracker
        if (killer == null) {
            val record = lastDamager[victim]
            // Si el último golpe ocurrió hace menos de 15 segundos (15,000 ms), cuenta como kill
            if (record != null && System.currentTimeMillis() - record.second < 15000) {
                killer = record.first
            }
        }

        // Sumar la kill al atacante
        if (killer != null && game.players.contains(killer) && killer != victim) {
            val currentKills = game.kills[killer] ?: 0
            game.kills[killer] = currentKills + 1
        }

        // Limpieza de memoria
        lastDamager.remove(victim)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDeath(e: PlayerDeathEvent) {
        val victim = e.entity
        val game = plugin.eventManager.currentGame as? Skywars ?: return

        if (game.isRunning && game.players.contains(victim)) {
            procesarKill(victim, game)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMoveToVoid(e: PlayerMoveEvent) {
        // Optimización brutal: Ignorar todo movimiento que no esté cerca del vacío
        if (e.to.y > e.to.world.minHeight + 8.0) return

        val p = e.player
        val game = plugin.eventManager.currentGame as? Skywars ?: return

        // Limpiar caché entre partidas
        if (game.phase != SwPhase.PLAYING) {
            lastDamager.clear()
            muertesProcesadas.clear()
            return
        }

        if (!game.players.contains(p)) return

        // Tu código elimina en "minHeight + 5", así que procesamos la kill 1 bloque antes (minHeight + 6)
        if (e.to.y <= e.to.world.minHeight + 6.0) {
            procesarKill(p, game)
        }
    }

    // --- 🎮 INTERACCIONES GENERALES (COFRES, MENÚS, ETC) 🎮 ---

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        val game = plugin.eventManager.currentGame as? Skywars ?: return
        if (!game.isRunning) return

        // 1. LLENAR COFRES AL ABRIRLOS Y CREAR HOLOGRAMA
        if (e.action == Action.RIGHT_CLICK_BLOCK) {
            val block = e.clickedBlock ?: return
            if (block.type == Material.CHEST || block.type == Material.TRAPPED_CHEST) {
                if (game.phase == SwPhase.PLAYING && !game.openedChests.contains(block.location)) {
                    game.openedChests.add(block.location)

                    // Llenar inventario
                    val state = block.state as org.bukkit.block.Chest
                    state.inventory.clear()
                    val loot = SkywarsManager.generarLoot(game.chosenChest, plugin)
                    loot.forEach { item ->
                        val slot = (0 until 27).random()
                        state.inventory.setItem(slot, item)
                    }

                    // Poner holograma encima
                    game.spawnHologramAtChest(block.location)
                }
            }
        }

        // 2. MENÚS DE PREPARACIÓN
        if (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK) {
            val item = e.item ?: return
            when (item.type) {
                Material.BLAZE_POWDER -> {
                    if (game.phase == SwPhase.TEAM_SELECT) {
                        e.isCancelled = true
                        abrirMenuEquipos(p, game)
                    }
                }
                Material.CHEST -> {
                    if (game.phase == SwPhase.VOTING) {
                        e.isCancelled = true
                        abrirMenuCofres(p, game)
                    }
                }
                Material.DRAGON_HEAD -> {
                    if (game.phase == SwPhase.VOTING) {
                        e.isCancelled = true
                        abrirMenuEventos(p, game)
                    }
                }
                else -> {}
            }
        }
    }

    // --- 🛡️ PREVENIR EXPLOIT DE COFRES Y ELIMINAR HOLOGRAMAS ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onChestBreak(e: BlockBreakEvent) {
        val game = plugin.eventManager.currentGame as? Skywars ?: return
        if (!game.isRunning || game.phase != SwPhase.PLAYING) return

        val block = e.block
        if (block.type == Material.CHEST || block.type == Material.TRAPPED_CHEST) {
            val loc = block.location

            // 1. Si el cofre tiene un holograma, lo eliminamos
            game.chestHolograms[loc]?.let { holo ->
                if (!holo.isDead) holo.remove()
                game.chestHolograms.remove(loc)
            }

            // 2. Si rompen un cofre que NUNCA fue abierto, soltamos el loot al suelo
            if (!game.openedChests.contains(loc)) {
                game.openedChests.add(loc) // Lo marcamos abierto
                val loot = SkywarsManager.generarLoot(game.chosenChest, plugin)
                loot.forEach { item ->
                    block.world.dropItemNaturally(loc, item)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onChestPlace(e: BlockPlaceEvent) {
        val game = plugin.eventManager.currentGame as? Skywars ?: return
        if (!game.isRunning || game.phase != SwPhase.PLAYING) return

        val block = e.blockPlaced
        if (block.type == Material.CHEST || block.type == Material.TRAPPED_CHEST) {
            game.openedChests.add(block.location)
        }
    }

    @EventHandler
    fun onDamage(e: EntityDamageEvent) {
        val p = e.entity as? Player ?: return
        val game = plugin.eventManager.currentGame as? Skywars ?: return
        if (!game.isRunning || !game.players.contains(p)) return

        // Inmunidad al daño de caída durante los primeros 5 segundos (timer de 600 a 595)
        if (e.cause == EntityDamageEvent.DamageCause.FALL) {
            if (game.phase == SwPhase.PLAYING && game.mainTimer >= 595) {
                e.isCancelled = true
            }
        }
    }

    // --- 📜 MENÚS ---

    private fun abrirMenuEquipos(p: Player, game: Skywars) {
        val gui = Gui.gui().title(Component.text("§8Selecciona Equipo")).rows(3).disableAllInteractions().create()
        val maxEquipos = (game.players.size / 2) + 1

        for (i in 1..maxEquipos) {
            val teamPlayers = game.players.filter { game.playerTeam[it] == i }
            val icon = if (teamPlayers.size >= 2) Material.RED_STAINED_GLASS_PANE else Material.GREEN_STAINED_GLASS_PANE
            val status = if (teamPlayers.size >= 2) "<red>Lleno</red>" else "<green>Click para unirte</green>"

            val lore = mutableListOf(status, "")
            teamPlayers.forEach { lore.add("<gray>- ${it.name}") }

            val item = ItemBuilder.from(icon).name(plugin.messageManager.parse("<yellow>Equipo $i</yellow>"))
                .lore(lore.map { plugin.messageManager.parse(it) }).asGuiItem { _ ->
                    if (teamPlayers.size < 2) {
                        game.playerTeam[p] = i
                        p.sendMessage(plugin.messageManager.parse("<green>Te has unido al Equipo $i.</green>"))
                        p.playSound(p.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                        gui.close(p)
                    }
                }
            gui.addItem(item)
        }
        gui.open(p)
    }

    private fun abrirMenuCofres(p: Player, game: Skywars) {
        val gui = Gui.gui().title(Component.text("§8Votar Cofres")).rows(1).disableAllInteractions().create()
        SkywarsChest.values().forEachIndexed { index, tipo ->
            val item = ItemBuilder.from(tipo.icono).name(plugin.messageManager.parse(tipo.nombre))
                .lore(plugin.messageManager.parse("<gray>Click para votar por este tipo.</gray>")).asGuiItem { _ ->
                    game.chestVotes[p.uniqueId] = tipo
                    p.sendMessage(plugin.messageManager.parse("<green>Has votado por: ${tipo.nombre}</green>"))
                    p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
                    gui.close(p)
                }
            gui.setItem(index + 3, item)
        }
        gui.open(p)
    }

    private fun abrirMenuEventos(p: Player, game: Skywars) {
        val gui = Gui.gui().title(Component.text("§8Votar Evento Final")).rows(1).disableAllInteractions().create()
        SkywarsEvent.values().forEachIndexed { index, tipo ->
            val item = ItemBuilder.from(tipo.icono).name(plugin.messageManager.parse(tipo.nombre))
                .lore(plugin.messageManager.parse("<gray>Click para votar por este evento.</gray>")).asGuiItem { _ ->
                    game.eventVotes[p.uniqueId] = tipo
                    p.sendMessage(plugin.messageManager.parse("<green>Has votado por: ${tipo.nombre}</green>"))
                    p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
                    gui.close(p)
                }
            gui.setItem(index + 3, item)
        }
        gui.open(p)
    }
}

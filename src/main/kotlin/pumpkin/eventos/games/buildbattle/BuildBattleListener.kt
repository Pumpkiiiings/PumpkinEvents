package pumpkin.eventos.games.buildbattle

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import pumpkin.eventos.PumpkinEventos
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BuildBattleListener(private val plugin: PumpkinEventos) : Listener {

    private fun game(): BuildBattle? = plugin.eventManager.currentGame as? BuildBattle

    // --- Selección pos1/pos2 por jugador ---
    private val pos1 = mutableMapOf<java.util.UUID, Location>()
    private val pos2 = mutableMapOf<java.util.UUID, Location>()

    // --- Protección de parcelas ---

    /** Devuelve el centro de la parcela asignada al jugador */
    private fun getPlotCenter(p: Player, game: BuildBattle): Location? {
        val plotIdx = game.playerPlot[p.uniqueId] ?: return null
        val world = game.gameWorld ?: return null
        return game.plotSpawns.getOrNull(plotIdx)?.clone()?.apply { this.world = world }
    }

    private fun isInsidePlot(loc: Location, center: Location, radius: Int): Boolean {
        if (loc.world != center.world) return false
        val height = plugin.config.getInt("buildbattle.plot_height", 15)
        // Usamos < (estricto) en X/Z para que la capa exterior (las paredes) sea intocable.
        // Con radius=16 en una parcela 31x31: interior buildable = 29x29, paredes = protegidas.
        return abs(loc.blockX - center.blockX) < radius &&
               abs(loc.blockZ - center.blockZ) < radius &&
               loc.blockY > center.blockY &&
               loc.blockY < center.blockY + height
    }

    // --- Partículas de selección ---
    private fun spawnSelectionParticles(p: Player, p1: Location, p2: Location) {
        val world = p1.world ?: return
        val minX = min(p1.blockX, p2.blockX).toDouble()
        val maxX = max(p1.blockX, p2.blockX).toDouble()
        val minY = min(p1.blockY, p2.blockY).toDouble()
        val maxY = max(p1.blockY, p2.blockY).toDouble()
        val minZ = min(p1.blockZ, p2.blockZ).toDouble()
        val maxZ = max(p1.blockZ, p2.blockZ).toDouble()

        val step = 1.0
        fun edge(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double) {
            var t = 0.0
            val dx = x2 - x1; val dy = y2 - y1; val dz = z2 - z1
            val dist = Math.sqrt(dx*dx + dy*dy + dz*dz)
            if (dist == 0.0) return
            while (t <= dist) {
                val frac = t / dist
                world.spawnParticle(Particle.DUST,
                    x1 + dx * frac, y1 + dy * frac + 0.5, z1 + dz * frac,
                    1, 0.0, 0.0, 0.0,
                    Particle.DustOptions(org.bukkit.Color.fromRGB(0, 200, 255), 1.0f))
                t += step
            }
        }
        // 4 aristas inferiores
        edge(minX, minY, minZ, maxX, minY, minZ)
        edge(minX, minY, maxZ, maxX, minY, maxZ)
        edge(minX, minY, minZ, minX, minY, maxZ)
        edge(maxX, minY, minZ, maxX, minY, maxZ)
        // 4 aristas superiores
        edge(minX, maxY, minZ, maxX, maxY, minZ)
        edge(minX, maxY, maxZ, maxX, maxY, maxZ)
        edge(minX, maxY, minZ, minX, maxY, maxZ)
        edge(maxX, maxY, minZ, maxX, maxY, maxZ)
        // 4 aristas verticales
        edge(minX, minY, minZ, minX, maxY, minZ)
        edge(maxX, minY, minZ, maxX, maxY, minZ)
        edge(minX, minY, maxZ, minX, maxY, maxZ)
        edge(maxX, minY, maxZ, maxX, maxY, maxZ)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        val game = game() ?: return
        if (!game.isRunning) return

        // Votación: LIME_DYE y RED_DYE
        if (game.phase == BbPhase.VOTING &&
            game.players.contains(p) &&
            e.hand == EquipmentSlot.HAND &&
            (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK)) {
            when (e.item?.type) {
                Material.LIME_DYE -> { e.isCancelled = true; game.registerVote(p, true) }
                Material.RED_DYE  -> { e.isCancelled = true; game.registerVote(p, false) }
                else -> {}
            }
        }

        // Palo de selección en fase BUILD
        if (game.phase != BbPhase.BUILD || !game.players.contains(p)) return
        val item = e.item ?: return
        val key = NamespacedKey(plugin, "bb_item")
        val tag = item.itemMeta?.persistentDataContainer?.get(key, PersistentDataType.STRING) ?: return
        if (tag != "wand") return

        e.isCancelled = true

        when (e.action) {
            Action.LEFT_CLICK_BLOCK -> {
                val loc = e.clickedBlock?.location ?: return
                pos1[p.uniqueId] = loc
                plugin.languageManager.send(p, "buildbattle.selection.pos1",
                    Placeholder.unparsed("x", loc.blockX.toString()), Placeholder.unparsed("y", loc.blockY.toString()), Placeholder.unparsed("z", loc.blockZ.toString()))
                p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f)
                val p2loc = pos2[p.uniqueId]
                if (p2loc != null) spawnSelectionParticles(p, loc, p2loc)
            }
            Action.RIGHT_CLICK_BLOCK -> {
                val loc = e.clickedBlock?.location ?: return
                pos2[p.uniqueId] = loc
                plugin.languageManager.send(p, "buildbattle.selection.pos2",
                    Placeholder.unparsed("x", loc.blockX.toString()), Placeholder.unparsed("y", loc.blockY.toString()), Placeholder.unparsed("z", loc.blockZ.toString()))
                p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.8f)
                val p1loc = pos1[p.uniqueId]
                if (p1loc != null) spawnSelectionParticles(p, p1loc, loc)
            }
            Action.RIGHT_CLICK_AIR -> {
                // Rellenar zona con el bloque en la mano secundaria (off-hand o slot actual)
                val fillMat = p.inventory.itemInOffHand.type.takeIf { it.isBlock && it != Material.AIR }
                    ?: p.inventory.itemInMainHand.type.takeIf { it.isBlock && it != Material.AIR }

                if (fillMat == null) {
                    plugin.languageManager.send(p, "buildbattle.selection.no_material")
                    return
                }

                val p1loc = pos1[p.uniqueId]
                val p2loc = pos2[p.uniqueId]
                if (p1loc == null || p2loc == null) {
                    plugin.languageManager.send(p, "buildbattle.selection.missing_positions")
                    return
                }

                val world = p1loc.world ?: return
                if (world != p.world) return

                val minX = min(p1loc.blockX, p2loc.blockX)
                val maxX = max(p1loc.blockX, p2loc.blockX)
                val minY = min(p1loc.blockY, p2loc.blockY)
                val maxY = max(p1loc.blockY, p2loc.blockY)
                val minZ = min(p1loc.blockZ, p2loc.blockZ)
                val maxZ = max(p1loc.blockZ, p2loc.blockZ)

                val volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)
                if (volume > 32768) {
                    plugin.languageManager.send(p, "buildbattle.selection.too_large")
                    return
                }

                // Verificar que la selección está dentro de la parcela
                val radius = plugin.config.getInt("buildbattle.plot_radius", 15)
                val center = getPlotCenter(p, game)
                if (center != null) {
                    if (!isInsidePlot(p1loc, center, radius) || !isInsidePlot(p2loc, center, radius)) {
                        plugin.languageManager.send(p, "buildbattle.selection.outside_plot")
                        return
                    }
                }

                // Rellenar en scheduler de región para evitar problemas de thread
                plugin.server.regionScheduler.run(plugin, p1loc) { _ ->
                    for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
                        world.getBlockAt(x, y, z).type = fillMat
                    }
                    plugin.server.regionScheduler.run(plugin, p.location) { _ ->
                        plugin.languageManager.send(p, "buildbattle.selection.filled",
                            Placeholder.unparsed("count", volume.toString()), Placeholder.unparsed("material", fillMat.name))
                        p.playSound(p.location, Sound.BLOCK_ANVIL_USE, 1f, 1.2f)
                    }
                }
            }
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBreak(e: BlockBreakEvent) {
        val p = e.player
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(p)) return
        if (game.phase != BbPhase.BUILD) { e.isCancelled = true; return }

        val radius = plugin.config.getInt("buildbattle.plot_radius", 15)
        val center = getPlotCenter(p, game) ?: return
        if (!isInsidePlot(e.block.location, center, radius)) {
            e.isCancelled = true
            plugin.languageManager.send(p, "buildbattle.selection.break_outside")
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlace(e: BlockPlaceEvent) {
        val p = e.player
        val game = game() ?: return
        if (!game.isRunning || !game.players.contains(p)) return
        if (game.phase != BbPhase.BUILD) { e.isCancelled = true; return }

        val radius = plugin.config.getInt("buildbattle.plot_radius", 15)
        val center = getPlotCenter(p, game) ?: return
        if (!isInsidePlot(e.block.location, center, radius)) {
            e.isCancelled = true
            plugin.languageManager.send(p, "buildbattle.selection.place_outside")
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPvP(e: org.bukkit.event.entity.EntityDamageByEntityEvent) {
        val game = game() ?: return
        if (!game.isRunning) return
        // PvP completamente desactivado en Build Battle (no se registra ningún hit)
        if (e.damager is Player || (e.damager is org.bukkit.entity.Projectile &&
                (e.damager as org.bukkit.entity.Projectile).shooter is Player)) {
            if (game.players.contains(e.entity) || game.spectators.contains(e.entity)) {
                e.isCancelled = true
            }
        }
    }

    // Menú de equipos (si isTeams)
    @EventHandler
    fun onEquipoSelect(e: PlayerInteractEvent) {
        val p = e.player
        val game = game() ?: return
        if (!game.isRunning || !game.isTeams) return
        if (!game.players.contains(p)) return
        if (e.hand != EquipmentSlot.HAND) return
        if (e.action != Action.RIGHT_CLICK_AIR && e.action != Action.RIGHT_CLICK_BLOCK) return
        if (e.item?.type != Material.BLAZE_POWDER) return
        e.isCancelled = true
        abrirMenuEquipos(p, game)
    }

    private fun abrirMenuEquipos(p: Player, game: BuildBattle) {
        val gui = dev.triumphteam.gui.guis.Gui.gui()
            .title(plugin.languageManager.component("buildbattle.team.menu_title"))
            .rows(3).disableAllInteractions().create()

        val maxEquipos = (game.players.size / 2) + 1
        for (i in 1..maxEquipos) {
            val teamPlayers = game.players.filter { game.playerTeam[it] == i }
            val icon = if (teamPlayers.size >= 2) Material.RED_STAINED_GLASS_PANE else Material.GREEN_STAINED_GLASS_PANE
            val status = plugin.languageManager.component(if (teamPlayers.size >= 2) "buildbattle.team.full" else "buildbattle.team.join_available")
            val lore = mutableListOf<net.kyori.adventure.text.Component?>(status, net.kyori.adventure.text.Component.empty())
            teamPlayers.forEach { member ->
                lore.add(plugin.languageManager.component("buildbattle.team.member", Placeholder.unparsed("player", member.name)))
            }
            val item = dev.triumphteam.gui.builder.item.ItemBuilder.from(icon)
                .name(plugin.languageManager.component("buildbattle.team.item", Placeholder.unparsed("team", i.toString())))
                .lore(lore)
                .asGuiItem { _ ->
                    if (teamPlayers.size < 2) {
                        game.playerTeam[p] = i
                        plugin.languageManager.send(p, "buildbattle.team.joined", Placeholder.unparsed("team", i.toString()))
                        p.playSound(p.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                        gui.close(p)
                    }
                }
            gui.addItem(item)
        }
        gui.open(p)
    }
}

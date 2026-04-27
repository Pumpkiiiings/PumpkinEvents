package pumpkin.eventos.games.miniwalls

import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
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
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos

class MiniWallsListener(private val plugin: PumpkinEventos, private val game: MiniWalls) : Listener {

    // --- PROTECCIÓN DE CONSTRUCCIÓN ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(e: BlockPlaceEvent) {
        if (!game.isRunning || !game.players.contains(e.player)) return

        if (e.block.y > game.maxHeightLimit) {
            e.isCancelled = true
            e.player.sendMessage(plugin.messageManager.parse("<red>¡Has alcanzado el límite de altura!</red>"))
            return
        }

        // Registrar el bloque para que pueda ser roto después
        game.placedBlocks.add(e.block.location)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onBlockBreak(e: BlockBreakEvent) {
        if (!game.isRunning || !game.players.contains(e.player)) return

        // 1. En Preparación o Lobby NADIE rompe NADA.
        if (game.phase == MwPhase.PREPARING || game.phase == MwPhase.LOBBY) {
            e.isCancelled = true
            return
        }

        val block = e.block

        // 2. DETECCIÓN DE GOLPE AL NEXO (Faro de Cristal)
        if (block.type == Material.BEACON) {
            e.isCancelled = true

            val nexoEntry = game.teamNexusLoc.entries.find { it.value.blockX == block.x && it.value.blockZ == block.z }
            if (nexoEntry != null) {
                val teamDefensor = nexoEntry.key
                val hpActual = game.teamNexusHP[teamDefensor] ?: 0

                if (hpActual <= 0) return

                val attackerTeam = game.playerTeam[e.player]
                if (teamDefensor == attackerTeam) {
                    e.player.sendMessage(plugin.messageManager.parse("<red>¡No puedes dañar a tu propio Nexo!</red>"))
                    return
                }

                // --- SISTEMA DE COOLDOWN DE DAÑO (5 Segundos) ---
                val now = System.currentTimeMillis()
                val lastHit = game.teamNexusCooldown[teamDefensor] ?: 0L

                if (now - lastHit < 5000) {
                    val restante = ((5000 - (now - lastHit)) / 1000).toInt() + 1
                    e.player.sendActionBar(plugin.messageManager.parse("<red>🛡️ Nexo Inmune por $restante s</red>"))
                    e.player.playSound(e.player.location, Sound.ITEM_SHIELD_BLOCK, 1f, 1.5f)
                    return
                }

                // --- APLICAR DAÑO (-2 de Vida) ---
                val nuevoHp = hpActual - 2
                game.teamNexusHP[teamDefensor] = nuevoHp
                game.teamNexusCooldown[teamDefensor] = now

                block.world.spawnParticle(org.bukkit.Particle.CRIT, block.location.add(0.5, 0.5, 0.5), 20, 0.5, 0.5, 0.5)
                block.world.playSound(block.location, Sound.BLOCK_GLASS_HIT, 1f, 0.5f)

                if (nuevoHp <= 0) {
                    block.setType(Material.AIR)
                    block.getRelative(0, 1, 0).setType(Material.AIR)
                    block.world.playSound(block.location, Sound.ENTITY_WITHER_DEATH, 1f, 1f)

                    plugin.server.broadcast(plugin.messageManager.parse("<newline>☠ ${teamDefensor.chatColor}<b>¡EL NEXO ${teamDefensor.displayName.uppercase()} HA SIDO DESTRUIDO!</b></${teamDefensor.chatColor.replace("<#", "<")}> <white>¡Ya no reaparecerán!</white><newline>"))
                } else {
                    game.players.filter { game.playerTeam[it] == teamDefensor }.forEach {
                        it.sendTitle("§c§l¡NEXO ATACADO!", "§fQuedan $nuevoHp HP", 5, 40, 10)
                        it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_HURT, 1f, 0.5f)
                    }
                    e.player.sendMessage(plugin.messageManager.parse("<green>🗡 ¡Le has bajado 2 de vida al nexo! (Espera 5s para volver a pegarle).</green>"))
                }
            }
            return
        }

        // 3. ¿Es un bloque puesto por otro jugador? -> SE PERMITE con drop vanilla
        if (game.placedBlocks.contains(block.location)) {
            game.placedBlocks.remove(block.location)
            e.isCancelled = false
            e.isDropItems = true
            return
        }

        // 4. ¿Es un bloque que está en la lista de 'breakable_blocks'? -> SE PERMITE
        val breakableBlocks = plugin.config.getStringList("miniwalls.breakable_blocks")
        if (breakableBlocks.contains(block.type.name)) {
            e.isCancelled = false
            e.isDropItems = true // Drop vanilla al romper
            return
        }

        // 5. Si no es nada de lo anterior, se cancela para proteger el mapa
        e.isCancelled = true
        e.player.sendMessage(plugin.messageManager.parse("<red>Solo puedes romper bloques de lana, o bloques puestos por jugadores.</red>"))
    }

    // --- MANEJO DE MENÚS Y CLICS ALTERNATIVOS ---
    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        if (!game.isRunning || !game.players.contains(p)) return

        // 1. Selección de Equipo en el Lobby
        if (game.phase == MwPhase.LOBBY) {
            val item = e.item ?: return
            if (item.type.name.endsWith("_WOOL") || item.type == Material.WHITE_WOOL) {
                e.isCancelled = true
                abrirMenuEquipos(p)
            }
            return
        }

        // 2. Golpe al Nexo por Clic Izquierdo (Para evitar que fallen la hitbox si están lejos)
        if (e.action == Action.LEFT_CLICK_BLOCK) {
            val block = e.clickedBlock ?: return
            if (block.type == Material.BEACON) {
                // Forzamos el evento de rotura para que pase por el filtro de daño de arriba
                val fakeEvent = BlockBreakEvent(block, p)
                plugin.server.pluginManager.callEvent(fakeEvent)
            }
        }
    }

    private fun abrirMenuEquipos(p: Player) {
        val gui = Gui.gui()
            .title(Component.text("§8Selecciona Equipo"))
            .rows(1)
            .disableAllInteractions()
            .create()

        val currentTeam = game.playerTeam[p]

        MwTeam.values().forEachIndexed { index, team ->
            val isSelected = currentTeam == team
            val teamCount = game.playerTeam.values.count { it == team }

            val lore = mutableListOf<Component>()
            lore.add(plugin.messageManager.parse("<gray>Jugadores: <white>$teamCount</white></gray>"))
            lore.add(Component.empty())

            if (isSelected) {
                lore.add(plugin.messageManager.parse("<green>¡Ya estás en este equipo!</green>"))
            } else {
                lore.add(plugin.messageManager.parse("<yellow>Click para unirte</yellow>"))
            }

            val itemBuilder = ItemBuilder.from(team.wool)
                .name(plugin.messageManager.parse("${team.chatColor}<b>${team.displayName}</b>"))
                .lore(lore.toList())

            if (isSelected) itemBuilder.glow(true)

            gui.setItem(index + 2, itemBuilder.asGuiItem { _ ->
                game.playerTeam[p] = team

                val newItem = ItemStack(team.wool).apply {
                    itemMeta = itemMeta?.apply {
                        displayName(plugin.messageManager.parse("<#00FFFF><b>🚩 Elegir Equipo</b></#00FFFF>"))
                    }
                }

                p.inventory.setItem(0, newItem)

                val msg = "<white>Seleccionaste el ${team.chatColor}<b>${team.displayName}</b></white>"
                p.sendMessage(plugin.messageManager.parse(msg))
                p.playSound(p.location, Sound.UI_BUTTON_CLICK, 1f, 1f)

                gui.close(p)
            })
        }
        gui.open(p)
    }

    // --- BLOQUEO DE DAÑO EN EL LOBBY Y FRIENDLY FIRE ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(e: EntityDamageEvent) {
        if (!game.isRunning) return
        val p = e.entity as? Player ?: return
        if (!game.players.contains(p)) return

        if (game.phase == MwPhase.LOBBY) {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPunch(e: EntityDamageByEntityEvent) {
        if (!game.isRunning) return
        val victim = e.entity as? Player ?: return

        var attacker = e.damager as? Player
        if (attacker == null && e.damager is Arrow) {
            attacker = (e.damager as Arrow).shooter as? Player
        }
        if (attacker == null) return

        if (game.players.contains(victim) && game.players.contains(attacker)) {
            if (game.phase == MwPhase.LOBBY || game.phase == MwPhase.PREPARING) {
                e.isCancelled = true
                return
            }

            // FRIENDLY FIRE BLOQUEADO
            if (game.playerTeam[victim] == game.playerTeam[attacker]) {
                e.isCancelled = true
            }
        }
    }

    // --- CURACIÓN POR KILL (Kits) ---
    @EventHandler
    fun onPlayerKill(e: PlayerDeathEvent) {
        if (!game.isRunning) return
        val killer = e.entity.killer ?: return
        if (!game.players.contains(killer)) return

        val kit = game.playerKit[killer] ?: return
        if (kit.healOnKill > 0.0) {
            killer.health = (killer.health + kit.healOnKill).coerceAtMost(killer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0)
            killer.playSound(killer.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
            val hearts = (kit.healOnKill / 2).toInt()
            killer.sendMessage(plugin.messageManager.parse("<#FF1493>❤ <b>+$hearts Corazones</b></#FF1493>"))
        }
    }
}

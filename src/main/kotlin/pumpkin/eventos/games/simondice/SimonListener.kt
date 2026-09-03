package pumpkin.eventos.games.simondice

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos

class SimonListener(private val plugin: PumpkinEventos) : Listener {

    private val lastValidAction = mutableMapOf<Player, Long>()

    init {
        // Watchdog: Penalizar a los que dejen de moverse en SALTAR o CAMINAR
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            val game = plugin.eventManager.currentGame as? SimonDice
            if (game == null || !game.isRunning) {
                lastValidAction.clear()
                return@runAtFixedRate
            }

            if (game.activeChallenges.contains("SALTAR") || game.activeChallenges.contains("CAMINAR")) {
                val now = System.currentTimeMillis()

                game.savedPlayers.toList().forEach { uuid ->
                    val p = plugin.server.getPlayer(uuid)
                    if (p != null && game.players.contains(p)) {
                        val lastAction = lastValidAction[p] ?: 0L

                        // 1.2 segundos sin salto o paso = Pierde la salvación
                        if (now - lastAction > 1200) {
                            game.removeSaved(p)
                        }
                    }
                }
            }
        }, 10L, 10L)
    }

    @EventHandler
    fun onMove(e: PlayerMoveEvent) {
        val p = e.player
        val game = plugin.eventManager.currentGame as? SimonDice ?: return
        if (!game.isRunning || !game.players.contains(p)) return

        val from = e.from
        val to = e.to

        // RETO: SALTAR
        if (game.activeChallenges.contains("SALTAR")) {
            if (to.y > from.y && !p.isOnGround && !p.location.block.getRelative(BlockFace.DOWN).type.isAir) {
                lastValidAction[p] = System.currentTimeMillis()
                if (!game.savedPlayers.contains(p.uniqueId)) {
                    plugin.server.globalRegionScheduler.run(plugin) { _ -> game.markSaved(p) }
                }
            }
        }

        // RETO: CAMINAR
        if (game.activeChallenges.contains("CAMINAR")) {
            val diffX = Math.abs(to.x - from.x)
            val diffZ = Math.abs(to.z - from.z)
            if (diffX > 0.05 || diffZ > 0.05) {
                lastValidAction[p] = System.currentTimeMillis()
                if (!game.savedPlayers.contains(p.uniqueId)) {
                    plugin.server.globalRegionScheduler.run(plugin) { _ -> game.markSaved(p) }
                }
            }
        }

        // RETO: QUIETO
        if (game.activeChallenges.contains("QUIETO")) {
            val diffX = Math.abs(to.x - from.x)
            val diffZ = Math.abs(to.z - from.z)
            if (diffX > 0.05 || diffZ > 0.05) {
                if (game.savedPlayers.contains(p.uniqueId)) {
                    game.removeSaved(p)
                }
            }
        }
    }

    @EventHandler
    fun onSneak(e: PlayerToggleSneakEvent) {
        val p = e.player
        val game = plugin.eventManager.currentGame as? SimonDice ?: return
        if (!game.isRunning || !game.players.contains(p)) return

        if (game.activeChallenges.contains("AGACHARSE")) {
            if (e.isSneaking) {
                plugin.server.globalRegionScheduler.run(plugin) { _ -> game.markSaved(p) }
            } else {
                game.removeSaved(p)
            }
        }
    }

    @EventHandler
    fun onHit(e: EntityDamageByEntityEvent) {
        val attacker = e.damager as? Player ?: return
        val victim = e.entity as? Player ?: return
        val game = plugin.eventManager.currentGame as? SimonDice ?: return

        if (!game.isRunning || !game.players.contains(attacker)) return

        // RETO: GOLPEAR
        if (game.activeChallenges.contains("GOLPEAR")) {
            e.isCancelled = true // Evitamos daño real
            if (!game.savedPlayers.contains(attacker.uniqueId)) {
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    game.markSaved(attacker)
                }
            }
            return
        }

        // RETO: CONSIGUE (Robo de ítem)
        if (game.activeChallenges.contains("CONSIGUE")) {
            e.damage = 0.001

            if (victim.inventory.contains(game.targetMaterial!!)) {
                val hits = (game.hitCounter[victim.uniqueId] ?: 0) + 1
                game.hitCounter[victim.uniqueId] = hits

                if (hits >= 3) {
                    victim.inventory.remove(game.targetMaterial!!)
                    game.savedPlayers.remove(victim.uniqueId)
                    victim.isGlowing = false

                    val dropLoc = victim.location.clone().add(0.0, 1.0, 0.0)
                    val drop = victim.world.dropItem(dropLoc, ItemStack(game.targetMaterial!!))
                    drop.velocity = attacker.location.direction.multiply(0.5)
                    drop.setGlowing(true)

                    plugin.languageManager.send(victim, "simon_game.listener.item_stolen")
                    victim.playSound(victim.location, Sound.ITEM_SHIELD_BREAK, 1f, 1f)
                    game.hitCounter[victim.uniqueId] = 0
                } else {
                    victim.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1f, 1f)
                }
            }
            return
        }

        // Si no hay ningún reto de PVP activo, bloqueamos los golpes entre jugadores
        e.isCancelled = true
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        val p = e.player
        val game = plugin.eventManager.currentGame as? SimonDice ?: return

        // Abrir Menú Manual
        if (game.mode == SimonMode.MANUAL && game.streamer?.uniqueId == p.uniqueId) {
            if (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK) {
                if (e.item?.type == Material.NETHER_STAR) {
                    e.isCancelled = true
                    SimonMenu.abrirPanelPrincipal(plugin, p, game)
                }
            }
        }
    }

    @EventHandler
    fun onChat(e: AsyncChatEvent) {
        val p = e.player
        val game = plugin.eventManager.currentGame as? SimonDice ?: return
        val mensaje = PlainTextComponentSerializer.plainText().serialize(e.message())

        // 1. INPUT DEL MENÚ DEL STREAMER
        if (SimonMenu.esperandoInput.containsKey(p.uniqueId)) {
            e.isCancelled = true
            val tipo = SimonMenu.esperandoInput.remove(p.uniqueId) ?: return

            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                if (!game.isRunning) return@run

                when (tipo) {
                    "FRASE" -> {
                        game.targetPhrase = mensaje
                        plugin.languageManager.send(p, "simon_game.listener.phrase_ready", Placeholder.unparsed("phrase", mensaje))
                        game.startChallenge(listOf("FRASE"), "✎ DIGAN: <white>$mensaje", 15)
                    }
                    "MATES" -> {
                        // En el futuro puedes añadir un evaluador, aquí harcodeo para el menú manual
                        plugin.languageManager.send(p, "simon_game.listener.math_unsupported")
                    }
                }
            }
            return
        }

        if (!game.isRunning || !game.players.contains(p) || game.savedPlayers.contains(p.uniqueId)) return

        // 2. RETO FRASE
        if (game.activeChallenges.contains("FRASE")) {
            if (mensaje.trim().equals(game.targetPhrase.trim(), ignoreCase = true)) {
                plugin.server.globalRegionScheduler.run(plugin) { _ -> game.markSaved(p) }
            }
        }
    }
}

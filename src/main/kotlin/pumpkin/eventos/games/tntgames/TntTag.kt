package pumpkin.eventos.games.tntgames

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.BorderShrinkManager
import pumpkin.eventos.games.EventGame
import java.util.concurrent.TimeUnit

class TntTag(plugin: PumpkinEventos) : EventGame(plugin, "tnttag", "<red>TNT Tag</red>") {

    val itPlayers = mutableSetOf<Player>()
    var timer = 30
    var isPreparation = true
    private var glowActivated = false
    private var gameSecondsElapsed = 0
    private val borderManager = BorderShrinkManager(plugin, this)

    override fun onStart() {
        timer = 10
        isPreparation = true
        itPlayers.clear()
        glowActivated = false
        gameSecondsElapsed = 0

        // Dar items especiales a todos
        val listener = plugin.tntTagListener
        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            players.forEach { p ->
                p.gameMode = org.bukkit.GameMode.ADVENTURE
                p.inventory.clear()
                listener.giveSpecialItems(p)
            }
        }, 10L)

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            timer--

            if (isPreparation) {
                val rawPrep = plugin.languageManager.get("tnttag.actionbar.preparation")
                val bar = plugin.messageManager.parse(rawPrep, Placeholder.parsed("time", timer.toString()))

                players.forEach { p ->
                    p.sendActionBar(bar)
                    p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1f)
                }
                if (timer <= 0) {
                    isPreparation = false
                    timer = 30
                    plugin.server.globalRegionScheduler.run(plugin) { _ -> selectNewIts() }
                }
                return@runAtFixedRate
            }

            itPlayers.forEach { p ->
                p.scheduler.run(plugin, { _ ->
                    p.world.spawnParticle(org.bukkit.Particle.SMALL_FLAME, p.location.add(0.0, 2.2, 0.0), 3, 0.1, 0.1, 0.1, 0.02)
                }, null)
                p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f)
            }

            val rawExp = plugin.languageManager.get("tnttag.actionbar.explosion")
            val bar = plugin.messageManager.parse(rawExp, Placeholder.parsed("time", timer.toString()))
            players.forEach { it.sendActionBar(bar) }

            // --- NUEVO SISTEMA DE DESASTRES POR VOTACIÓN ---
            // Cuando faltan 15 segundos para que explote, iniciamos la votación (solo si quedan más de 2 vivos)
            if (timer == 15 && players.size > 2) {
                // El ExtrasVoteManager respetará el config.yml (solo iniciará si no hay otra activa y el juego está en la lista permitida)
                plugin.extrasVoteManager.startVoting()
            }

            if (timer <= 0) {
                val victims = itPlayers.toList()
                victims.forEach { p ->
                    p.scheduler.run(plugin, { _ ->
                        p.world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, p.location, 1)
                        p.world.playSound(p.location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f)
                        p.inventory.helmet = null
                        eliminate(p)
                    }, null)
                }

                if (players.size > 0) {
                    timer = 30
                    plugin.server.globalRegionScheduler.runDelayed(plugin, { _ -> selectNewIts() }, 2L)
                }
            }

            // Border shrink: arrancar a los 5 minutos (300s) de juego real (sin contar preparación)
            if (!isPreparation) {
                gameSecondsElapsed++
                if (gameSecondsElapsed == 300) {
                    val world = gameWorld ?: return@runAtFixedRate
                    val center = currentArena?.centerLocation
                    val cx = center?.x ?: 0.0
                    val cz = center?.z ?: 0.0
                    plugin.server.asyncScheduler.runNow(plugin) { _ ->
                        borderManager.start(world, cx, cz, delaySeconds = 0L)
                    }
                }
            }

        }, 1, 1, TimeUnit.SECONDS)
    }

    fun handlePunch(attacker: Player, victim: Player) {
        if (isPreparation || !itPlayers.contains(attacker) || itPlayers.contains(victim) || !players.contains(victim)) return

        itPlayers.remove(attacker)
        itPlayers.add(victim)

        attacker.inventory.helmet = null
        victim.inventory.helmet = ItemStack(Material.TNT)

        attacker.playSound(attacker.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
        victim.playSound(victim.location, Sound.ENTITY_CREEPER_PRIMED, 1f, 1.2f)

        // Broadcast del pase de TNT
        val rawPass = plugin.languageManager.get("tnttag.tnt_passed")
        plugin.server.broadcast(plugin.messageManager.parse(
            rawPass,
            Placeholder.parsed("attacker", attacker.name),
            Placeholder.parsed("victim", victim.name)
        ))

        // Glow si hay pocos jugadores
        checkGlowThreshold()
    }

    private fun checkGlowThreshold() {
        val threshold = plugin.config.getInt("tnttag.glow_threshold", 6)
        if (!glowActivated && players.size <= threshold) {
            glowActivated = true
            val rawGlow = plugin.languageManager.get("tnttag.glow_activated")
            plugin.server.broadcast(plugin.messageManager.parse(
                rawGlow, Placeholder.parsed("count", threshold.toString())
            ))
            players.forEach { p ->
                p.isGlowing = true
            }
        }
    }


    private fun selectNewIts() {
        itPlayers.clear()
        val amount = (players.size / 4).coerceAtLeast(1)

        val titleMain = plugin.messageManager.parse(plugin.languageManager.get("tnttag.title.has_tnt_main"))
        val titleSub = plugin.messageManager.parse(plugin.languageManager.get("tnttag.title.has_tnt_sub"))

        players.shuffled().take(amount).forEach { p ->
            itPlayers.add(p)
            p.inventory.helmet = ItemStack(Material.TNT)
            p.showTitle(Title.title(titleMain, titleSub))
        }
    }

    override fun checkWinner() {
        val winner = players.firstOrNull() ?: return

        val rawWin = plugin.languageManager.get("tnttag.broadcast.winner")
        plugin.server.broadcast(plugin.messageManager.parse(rawWin, Placeholder.parsed("player", winner.name)))
        plugin.puntajeManager.addPoints(winner, 10, "¡Victoria conseguida!")
        stop()
    }

    override fun onStop() {
        gameWorld?.let { borderManager.stop(it) }
        plugin.eventManager.currentGame = null

        val lobby = plugin.arenaManager.mainLobby ?: plugin.server.worlds[0].spawnLocation
        val todos = players + spectators

        todos.forEach { p ->
            p.isGlowing = false
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.gameMode = org.bukkit.GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
    }
}

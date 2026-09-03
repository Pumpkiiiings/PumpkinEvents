package pumpkin.eventos.games.hideandseek

import me.libraryaddict.disguise.DisguiseAPI
import me.libraryaddict.disguise.disguisetypes.DisguiseType
import me.libraryaddict.disguise.disguisetypes.MobDisguise
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class HideAndSeek(plugin: PumpkinEventos) : EventGame(plugin, "hideandseek", "<#FF8C00>Hide & Seek</#FF8C00>") {

    companion object {
        val hunterCounts = mutableMapOf<UUID, Int>()
    }


    val cazadores = mutableSetOf<Player>()
    val escondites = mutableSetOf<Player>()

    val disguisedPlayers = mutableMapOf<UUID, DisguiseType>()
    val spawnedDecoys = mutableListOf<Entity>()
    var hunterEyeCooldown = mutableMapOf<Player, Long>()

    var isPreparation = true
    var prepTimer = 30
    var gameTimer = 300

    // --- SÓLO ANIMALES DE GRANJA ---
    private val availableMobs = arrayOf(
        DisguiseType.COW,
        DisguiseType.PIG,
        DisguiseType.SHEEP,
        DisguiseType.CHICKEN
    )

    override fun getExtraPlaceholders(): Map<String, String> {
        val timer = if (isPreparation) prepTimer else gameTimer
        val min = timer / 60
        val sec = timer % 60
        return mapOf(
            "%cazador%" to if (cazadores.isEmpty()) "Nadie" else cazadores.first().name,
            "%vivos%" to escondites.size.toString(),
            "%time_left%" to String.format("%02d:%02d", min, sec)
        )
    }

    override fun getCustomGameRules(): Map<GameRule<*>, Any> {
        return mapOf(
            GameRule.FALL_DAMAGE to false, // Daño de caída desactivado (para que los pollos/cerdos puedan tirarse sin morir tontamente)
            GameRule.NATURAL_REGENERATION to true
        )
    }

    override fun onStart() {
        val allPlayers = players.toList()
        if (allPlayers.isEmpty()) { stop(); return }

        val minCount = allPlayers.minOfOrNull { hunterCounts.getOrDefault(it.uniqueId, 0) } ?: 0
        val candidates = allPlayers.filter { hunterCounts.getOrDefault(it.uniqueId, 0) == minCount }
        
        val firstHunter = candidates.random()
        hunterCounts[firstHunter.uniqueId] = (hunterCounts[firstHunter.uniqueId] ?: 0) + 1

        cazadores.add(firstHunter)
        escondites.addAll(allPlayers.filter { it != firstHunter })

        isPreparation = true
        prepTimer = plugin.config.getInt("hideandseek.preparation_seconds", 30)
        gameTimer = plugin.config.getInt("hideandseek.game_duration_seconds", 300)
        hunterEyeCooldown.clear()
        disguisedPlayers.clear()
        spawnedDecoys.clear()

        val arena = currentArena ?: return
        val targetWorld = gameWorld ?: arena.centerLocation?.world ?: return

        plugin.server.globalRegionScheduler.runDelayed(plugin, { _ ->
            allPlayers.forEach { p ->
                p.inventory.clear()
                p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
                p.gameMode = GameMode.ADVENTURE

                if (cazadores.contains(p)) {
                    // Cazador congelado y ciego
                    p.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, prepTimer * 20, 1, false, false))
                    p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, prepTimer * 20, 255, false, false))
                    p.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, prepTimer * 20, 250, false, false))

                    // Asegurarnos de que el jugador aparezca en un spawn o centro
                    p.teleportAsync(arena.spawnPoints.firstOrNull()?.clone()?.apply { world = targetWorld } ?: arena.centerLocation!!)

                    val titleMain = plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hunter.title_main"))
                    val titleSub = plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hunter.title_sub"))
                    p.showTitle(Title.title(titleMain, titleSub))
                } else {
                    // Escondites
                    p.teleportAsync(arena.spawnPoints.lastOrNull()?.clone()?.apply { world = targetWorld } ?: arena.centerLocation!!)

                    val titleMain = plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hider.title_main"))
                    val titleSub = plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hider.title_sub"))
                    p.showTitle(Title.title(titleMain, titleSub))

                    val mobType = availableMobs.random()
                    disguisePlayer(p, mobType)

                    // Opcional: Darles una espadita de madera a los escondidos para que puedan defenderse
                    p.inventory.addItem(ItemStack(Material.WOODEN_SWORD))
                }
            }

            // Llenar el mapa de animales falsos (decoys) para confundir al cazador
            spawnDecoyMobs(arena.centerLocation!!.apply { world = targetWorld }, escondites.toList())

        }, 10L) // Delay para seguridad del TP

        runAtFixedRate( { task ->

            if (isPreparation) {
                prepTimer--

                val rawMsg = plugin.languageManager.get("hideandseek.actionbar.preparation")
                val actionMsg = plugin.messageManager.parse(rawMsg.ifEmpty { "<yellow>Prepárate... $prepTimer s</yellow>" }, Placeholder.parsed("time", prepTimer.toString()))
                allPlayers.forEach { p -> p.sendActionBar(actionMsg) }

                if (prepTimer <= 0) {
                    isPreparation = false
                    val releasedMsg = plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hunter_released").ifEmpty { "<red>¡EL CAZADOR HA SIDO LIBERADO!</red>" })
                    plugin.server.broadcast(releasedMsg)

                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        cazadores.forEach { c ->
                            c.activePotionEffects.forEach { c.removePotionEffect(it.type) } // Quitamos ceguera/lentitud
                            c.playSound(c.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                            c.gameMode = GameMode.SURVIVAL

                            val eye = ItemStack(Material.ENDER_EYE)
                            val eyeMeta = eye.itemMeta
                            eyeMeta?.displayName(plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hunter.eye_item_name").ifEmpty { "<#00FFFF>Ojo Revelador</#00FFFF>" }))
                            eye.itemMeta = eyeMeta
                            c.inventory.setItem(0, eye)
                            c.inventory.setItem(1, ItemStack(Material.DIAMOND_SWORD))
                        }
                    }
                }
                return@runAtFixedRate
            }

            gameTimer--

            if (gameTimer <= 0) {
                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                    val winMsg = plugin.languageManager.get("hideandseek.broadcast.hiders_win")
                    plugin.server.broadcast(plugin.messageManager.parse(winMsg.ifEmpty { "<green>¡Los escondidos ganaron!</green>" }))
                    escondites.forEach { plugin.puntajeManager.addPoints(it, 15, "¡Sobreviviste!") }
                    stop()
                }
                task.cancel()
                return@runAtFixedRate
            }
        }, 20L, 20L)
    }

    fun disguisePlayer(player: Player, mobType: DisguiseType) {
        val disguise = MobDisguise(mobType)

        disguise.isReplaceSounds = true
        // --- AHORA PUEDES VER TU PROPIO CUERPO ---
        disguise.isSelfDisguiseVisible = true

        DisguiseAPI.disguiseToAll(player, disguise)
        disguisedPlayers[player.uniqueId] = mobType
    }

    private fun spawnDecoyMobs(center: Location, hiders: List<Player>) {
        val typeCount = mutableMapOf<DisguiseType, Int>()
        for (uuid in disguisedPlayers.keys) {
            val type = disguisedPlayers[uuid] ?: continue
            typeCount[type] = typeCount.getOrDefault(type, 0) + 1
        }

        for ((type, count) in typeCount) {
            val entityType = disguiseTypeToEntity(type)
            val amount = count * plugin.config.getInt("hideandseek.decoys_per_player", 4)

            for (i in 0 until amount) {
                val spawnLoc = getRandomNearby(center, plugin.config.getInt("hideandseek.decoy_spawn_radius", 40))
                val mob = center.world.spawnEntity(spawnLoc, entityType)

                if (mob is Mob) {
                    mob.setAI(true)
                    mob.removeWhenFarAway = false
                    mob.isCustomNameVisible = false
                    spawnedDecoys.add(mob)
                }
            }
        }
    }

    private fun disguiseTypeToEntity(type: DisguiseType): EntityType {
        return when (type) {
            DisguiseType.COW -> EntityType.COW
            DisguiseType.PIG -> EntityType.PIG
            DisguiseType.SHEEP -> EntityType.SHEEP
            DisguiseType.CHICKEN -> EntityType.CHICKEN
            else -> EntityType.PIG
        }
    }

    private fun getRandomNearby(center: Location, radius: Int): Location {
        // Encontrar un bloque sólido seguro al azar para el Decoy
        val x = center.x + (Random.nextDouble() * radius * 2) - radius
        val z = center.z + (Random.nextDouble() * radius * 2) - radius

        var highestY = center.blockY
        for (y in (center.blockY + 20) downTo (center.blockY - 20)) {
            val block = center.world.getBlockAt(x.toInt(), y, z.toInt())
            if (block.type.isSolid) {
                highestY = y + 1
                break
            }
        }
        return Location(center.world, x, highestY.toDouble(), z)
    }

    override fun eliminate(player: Player) {
        super.eliminate(player) // Lo hace espectador
        escondites.remove(player)
        cazadores.remove(player)

        DisguiseAPI.undisguiseToAll(player)
        disguisedPlayers.remove(player.uniqueId)

        if (escondites.isEmpty()) {
            val winMsg = plugin.languageManager.get("hideandseek.broadcast.hunter_wins")
            plugin.server.broadcast(plugin.messageManager.parse(winMsg.ifEmpty { "<red>¡Los cazadores aniquilaron a todos!</red>" }))
            cazadores.forEach { plugin.puntajeManager.addPoints(it, 20, "¡Cazador Implacable!") }
            stop()
        }
    }

    // Método para convertir en cazador (Infección)
    fun convertToHunter(player: Player) {
        escondites.remove(player)
        cazadores.add(player)

        DisguiseAPI.undisguiseToAll(player)
        disguisedPlayers.remove(player.uniqueId)

        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
        player.gameMode = GameMode.SURVIVAL

        val eye = ItemStack(Material.ENDER_EYE)
        val eyeMeta = eye.itemMeta
        eyeMeta?.displayName(plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hunter.eye_item_name").ifEmpty { "<#00FFFF>Ojo Revelador</#00FFFF>" }))
        eye.itemMeta = eyeMeta
        player.inventory.setItem(0, eye)
        player.inventory.setItem(1, ItemStack(Material.DIAMOND_SWORD))

        val titleMain = plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hunter.title_main"))
        val titleSub = plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hunter.infected_sub"))
        player.showTitle(Title.title(titleMain, titleSub))

        val rawDie = plugin.languageManager.get("hideandseek.broadcast.hider_killed")
        plugin.server.broadcast(plugin.messageManager.parse(rawDie.ifEmpty { "<red>☠ <player> fue cazado y ahora es Cazador!</red>" }, Placeholder.parsed("player", player.name)))

        if (escondites.isEmpty()) {
            val winMsg = plugin.languageManager.get("hideandseek.broadcast.hunter_wins")
            plugin.server.broadcast(plugin.messageManager.parse(winMsg.ifEmpty { "<red>¡Los cazadores aniquilaron a todos!</red>" }))
            cazadores.forEach { plugin.puntajeManager.addPoints(it, 20, "¡Cazadores Implacables!") }
            stop()
        }
    }

    override fun checkWinner() { }

    override fun onStop() {
        returnToLobby(beforeReset = { DisguiseAPI.undisguiseToAll(it) })

        spawnedDecoys.forEach { it.remove() }
        spawnedDecoys.clear()

        disguisedPlayers.clear()
        escondites.clear()
        cazadores.clear()
    }
}

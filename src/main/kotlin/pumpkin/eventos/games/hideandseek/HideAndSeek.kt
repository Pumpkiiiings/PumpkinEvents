package pumpkin.eventos.games.hideandseek

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import io.github.retrooper.packetevents.util.SpigotConversionUtil

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.EventGame
import java.util.Optional
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DisguiseData(
    var type: Material = Material.HAY_BLOCK,
    var entityId: Int = 0,
    var uuid: UUID = UUID.randomUUID(),
    var lastMoveTime: Long = System.currentTimeMillis(),
    var isSolid: Boolean = false,
    var solidLocation: org.bukkit.Location? = null
)

class HideAndSeek(plugin: PumpkinEventos) : EventGame(plugin, "hideandseek", "<#FF8C00>Hide & Seek</#FF8C00>") {

    var cazador: Player? = null
    val escondites = mutableSetOf<Player>()
    val disguises = mutableMapOf<Player, DisguiseData>()

    var isPreparation = true
    var prepTimer = 30
    var gameTimer = 300

    var hunterEyeCooldown = mutableMapOf<Player, Long>()

    override fun getExtraPlaceholders(): Map<String, String> {
        val timer = if (isPreparation) prepTimer else gameTimer
        val min = timer / 60
        val sec = timer % 60
        return mapOf(
            "%cazador%" to (cazador?.name ?: "Nadie"),
            "%vivos%" to escondites.size.toString(),
            "%time_left%" to String.format("%02d:%02d", min, sec)
        )
    }

    override fun onStart() {
        val allPlayers = players.toList()
        if (allPlayers.isEmpty()) { stop(); return }

        cazador = allPlayers.random()
        escondites.addAll(allPlayers.filter { it != cazador })

        isPreparation = true
        prepTimer = plugin.config.getInt("hideandseek.preparation_seconds", 30)
        gameTimer = plugin.config.getInt("hideandseek.game_duration_seconds", 300)
        hunterEyeCooldown.clear()

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            allPlayers.forEach { p ->
                p.inventory.clear()
                p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
                p.gameMode = GameMode.ADVENTURE
                
                if (p == cazador) {
                    p.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, prepTimer * 20, 1, false, false))
                    p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, prepTimer * 20, 255, false, false))
                    p.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, prepTimer * 20, 250, false, false))
                    
                    val titleMain = plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hunter.title_main"))
                    val titleSub = plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hunter.title_sub"))
                    p.showTitle(Title.title(titleMain, titleSub))
                } else {
                    val titleMain = plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hider.title_main"))
                    val titleSub = plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hider.title_sub"))
                    p.showTitle(Title.title(titleMain, titleSub))

                    allPlayers.forEach { viewer ->
                        viewer.hidePlayer(plugin, p)
                    }

                    val wand = ItemStack(Material.BLAZE_ROD)
                    val meta = wand.itemMeta
                    meta?.displayName(plugin.messageManager.parse("<#FFAA00><b>Réplica Bloques</b> <gray>(Click Derecho)</gray></#FFAA00>"))
                    wand.itemMeta = meta
                    p.inventory.setItem(0, wand)

                    spawnDisguise(p, Material.HAY_BLOCK)
                }
            }
        }

        plugin.server.asyncScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            if (isPreparation) {
                prepTimer--
                
                val rawMsg = plugin.languageManager.get("hideandseek.actionbar.preparation")
                val actionMsg = plugin.messageManager.parse(rawMsg, Placeholder.parsed("time", prepTimer.toString()))
                allPlayers.forEach { p -> p.sendActionBar(actionMsg) }

                if (prepTimer <= 0) {
                    isPreparation = false
                    val releasedMsg = plugin.messageManager.parse(plugin.languageManager.get("hideandseek.hunter_released"))
                    plugin.server.broadcast(releasedMsg)
                    
                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        cazador?.let { c ->
                            c.activePotionEffects.forEach { c.removePotionEffect(it.type) }
                            c.playSound(c.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                            
                            val eye = ItemStack(Material.ENDER_EYE)
                            val eyeMeta = eye.itemMeta
                            eyeMeta?.displayName(plugin.messageManager.parse("<#FF0000><b>El Ojo que Todo lo Ve</b> <gray>(Click Derecho)</gray></#FF0000>"))
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
                    plugin.server.broadcast(plugin.messageManager.parse(winMsg))
                    escondites.forEach { plugin.puntajeManager.addPoints(it, 15, "¡Victoria escondido!") }
                    stop()
                }
                task.cancel()
                return@runAtFixedRate
            }
        }, 1, 1, TimeUnit.SECONDS)

        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task ->
            if (!isRunning) { task.cancel(); return@runAtFixedRate }

            val now = System.currentTimeMillis()
            val solidTimeLimit = plugin.config.getLong("hideandseek.solidify_seconds", 5) * 1000

            escondites.forEach { p ->
                val data = disguises[p] ?: return@forEach

                if (!data.isSolid) {
                    val loc = p.location
                    val teleportPacket = WrapperPlayServerEntityTeleport(
                        data.entityId,
                        Vector3d(loc.x, loc.y, loc.z),
                        loc.yaw, loc.pitch, true
                    )
                    
                    p.world.players.forEach { viewer ->
                        if (viewer != p) {
                            PacketEvents.getAPI().playerManager.sendPacket(viewer, teleportPacket)
                        }
                    }

                    if (now - data.lastMoveTime >= solidTimeLimit) {
                        solidifyPlayer(p, data)
                    }
                } else {
                    val blockLoc = data.solidLocation
                    if (blockLoc != null && now % 20 == 0L) { 
                        val bData = data.type.createBlockData()
                        p.world.players.forEach { it.sendBlockChange(blockLoc, bData) }
                    }
                }
            }
        }, 1, 1)
    }

    fun spawnDisguise(player: Player, type: Material) {
        val oldData = disguises[player]
        if (oldData != null && !oldData.isSolid) {
            val destroyPacket = WrapperPlayServerDestroyEntities(oldData.entityId)
            player.world.players.forEach { viewer ->
                if (viewer != player) {
                    PacketEvents.getAPI().playerManager.sendPacket(viewer, destroyPacket)
                }
            }
        }

        val fakeId = (Math.random() * 1000000.0).toInt() + 100000 
        val loc = player.location
        val blockData = type.createBlockData()
        val globalId = SpigotConversionUtil.fromBukkitBlockData(blockData).globalId

        val spawnPacket = WrapperPlayServerSpawnEntity(
            fakeId,
            Optional.of(UUID.randomUUID()),
            EntityTypes.FALLING_BLOCK,
            Vector3d(loc.x, loc.y, loc.z),
            0f, 0f, 0f,
            globalId,
            Optional.empty()
        )

        player.world.players.forEach { viewer ->
            if (viewer != player) {
                PacketEvents.getAPI().playerManager.sendPacket(viewer, spawnPacket)
            }
        }

        disguises[player] = DisguiseData(
            type = type,
            entityId = fakeId,
            uuid = spawnPacket.uuid.get(),
            lastMoveTime = System.currentTimeMillis(),
            isSolid = false
        )
    }

    fun solidifyPlayer(player: Player, data: DisguiseData) {
        data.isSolid = true
        data.solidLocation = player.location.block.location
        
        val destroyPacket = WrapperPlayServerDestroyEntities(data.entityId)
        player.world.players.forEach { viewer ->
            if (viewer != player) {
                PacketEvents.getAPI().playerManager.sendPacket(viewer, destroyPacket)
            }
        }
        
        val bData = data.type.createBlockData()
        player.world.players.forEach { it.sendBlockChange(data.solidLocation!!, bData) }
        
        player.sendMessage(plugin.messageManager.parse(plugin.languageManager.get("hideandseek.solidified")))
        player.playSound(player.location, Sound.BLOCK_STONE_PLACE, 1f, 1f)
    }

    fun unsolidifyPlayer(player: Player, data: DisguiseData) {
        if (!data.isSolid) return
        data.isSolid = false
        
        val realBlock = data.solidLocation?.block?.blockData ?: Material.AIR.createBlockData()
        player.world.players.forEach { it.sendBlockChange(data.solidLocation!!, realBlock) }
        data.solidLocation = null
        
        spawnDisguise(player, data.type)
        player.sendMessage(plugin.messageManager.parse(plugin.languageManager.get("hideandseek.unsolidified")))
    }

    override fun eliminate(player: Player) {
        super.eliminate(player)
        escondites.remove(player)
        
        val data = disguises.remove(player)
        if (data != null) {
            if (data.isSolid && data.solidLocation != null) {
                val realBlock = data.solidLocation!!.block.blockData
                player.world.players.forEach { it.sendBlockChange(data.solidLocation!!, realBlock) }
            } else {
                val destroyPacket = WrapperPlayServerDestroyEntities(data.entityId)
                player.world.players.forEach { viewer ->
                    if (viewer != player) {
                        PacketEvents.getAPI().playerManager.sendPacket(viewer, destroyPacket)
                    }
                }
            }
        }

        players.forEach { viewer ->
            viewer.showPlayer(plugin, player)
        }

        val rawDie = plugin.languageManager.get("hideandseek.broadcast.hider_killed")
        plugin.server.broadcast(plugin.messageManager.parse(rawDie, Placeholder.parsed("player", player.name)))

        if (escondites.isEmpty()) {
            val winMsg = plugin.languageManager.get("hideandseek.broadcast.hunter_wins")
            plugin.server.broadcast(plugin.messageManager.parse(winMsg))
            cazador?.let { plugin.puntajeManager.addPoints(it, 20, "¡Victoria cazador!") }
            stop()
        }
    }

    override fun checkWinner() { }

    override fun onStop() {
        plugin.eventManager.currentGame = null
        val lobby = plugin.arenaManager.mainLobby ?: plugin.server.worlds[0].spawnLocation
        
        (players + spectators).forEach { p ->
            (players + spectators).forEach { viewer ->
                viewer.showPlayer(plugin, p)
            }
            
            p.inventory.clear()
            p.activePotionEffects.forEach { p.removePotionEffect(it.type) }
            p.gameMode = GameMode.ADVENTURE
            p.teleportAsync(lobby)
        }
        
        disguises.values.forEach { data ->
            if (data.isSolid && data.solidLocation != null) {
                val realBlock = data.solidLocation!!.block.blockData
                plugin.server.onlinePlayers.forEach { it.sendBlockChange(data.solidLocation!!, realBlock) }
            } else {
                val destroyPacket = WrapperPlayServerDestroyEntities(data.entityId)
                plugin.server.onlinePlayers.forEach { viewer ->
                    PacketEvents.getAPI().playerManager.sendPacket(viewer, destroyPacket)
                }
            }
        }
        disguises.clear()
        escondites.clear()
        cazador = null
    }
}


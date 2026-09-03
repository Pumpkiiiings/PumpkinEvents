package pumpkin.eventos.games.battleroyale

import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos

class GlowPacketListener(private val plugin: PumpkinEventos) : PacketListenerAbstract() {
    override fun onPacketSend(event: PacketSendEvent) {
        if (event.packetType != PacketType.Play.Server.ENTITY_METADATA) return
        
        val viewer = event.getPlayer() as? Player ?: return
        val game = plugin.eventManager.currentGame as? BattleRoyale ?: return
        if (!game.isDuos || game.phase != BRPhase.PLAYING) return
        
        val wrapper = WrapperPlayServerEntityMetadata(event)
        val entityId = wrapper.entityId
        
        val target = game.players.find { it.entityId == entityId } ?: return
        if (viewer == target) return
        
        val viewerTeam = game.playerTeam[viewer]
        val targetTeam = game.playerTeam[target]
        
        // Si no son del mismo equipo (o son null), quitamos el glow
        if (viewerTeam == null || targetTeam == null || viewerTeam != targetTeam) {
            var modified = false
            val newList = wrapper.entityMetadata.map { data ->
                if (data.index == 0 && data.value is Byte) {
                    val currentVal = (data.value as Byte).toInt()
                    // Check if glow flag (0x40) is set
                    if ((currentVal and 0x40) != 0) {
                        modified = true
                        EntityData(data.index, com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes.BYTE, (currentVal and 0x40.inv()).toByte())
                    } else {
                        data
                    }
                } else {
                    data
                }
            }
            if (modified) {
                wrapper.entityMetadata = newList
            }
        }
    }
}

package pumpkin.eventos.utils

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

object OpenBoatUtilsManager {

    private const val CHANNEL_SETTINGS = "openboatutils:settings"

    // Packet IDs (0-indexed based on OpenBoatUtils protocol documentation)
    private const val PACKET_RESET: Short = 0
    private const val PACKET_SET_EXCLUSIVE_MODE: Short = 18
    private const val PACKET_SET_COLLISION_MODE: Short = 27
    private const val PACKET_SET_TEN_STEP_INTERPOLATION: Short = 29

    // Mode IDs
    const val MODE_RALLY: Short = 8

    // Collision Mode Ordinals
    const val COLLISION_NO_BOATS_OR_PLAYERS: Short = 1

    /**
     * Sends a packet to the player.
     */
    private fun sendPacket(plugin: Plugin, player: Player, data: ByteArray) {
        player.sendPluginMessage(plugin, CHANNEL_SETTINGS, data)
    }

    /**
     * Resets the boat settings to Vanilla for the given player.
     */
    fun sendReset(plugin: Plugin, player: Player) {
        val buf = PacketByteBuf()
        try {
            buf.writeShort(PACKET_RESET)
            sendPacket(plugin, player, buf.toBytes())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Enables or disables 10-step interpolation for the boat rendering.
     */
    fun sendTenStepInterpolation(plugin: Plugin, player: Player, enabled: Boolean) {
        val buf = PacketByteBuf()
        try {
            buf.writeShort(PACKET_SET_TEN_STEP_INTERPOLATION)
            buf.writeBoolean(enabled)
            sendPacket(plugin, player, buf.toBytes())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Applies a specific mode, resetting settings first.
     */
    fun sendExclusiveMode(plugin: Plugin, player: Player, modeId: Short) {
        val buf = PacketByteBuf()
        try {
            buf.writeShort(PACKET_SET_EXCLUSIVE_MODE)
            buf.writeShort(modeId)
            sendPacket(plugin, player, buf.toBytes())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Sets the collision mode for the boat.
     */
    fun sendCollisionMode(plugin: Plugin, player: Player, collisionOrdinal: Short) {
        val buf = PacketByteBuf()
        try {
            buf.writeShort(PACKET_SET_COLLISION_MODE)
            buf.writeShort(collisionOrdinal)
            sendPacket(plugin, player, buf.toBytes())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

/**
 * Utility class to write data matching Minecraft's PacketByteBuf formatting.
 */
class PacketByteBuf {
    private val byteArrayOutputStream = ByteArrayOutputStream()
    private val out = DataOutputStream(byteArrayOutputStream)

    @Throws(IOException::class)
    fun writeFloat(v: Float): PacketByteBuf {
        out.writeFloat(v)
        return this
    }

    @Throws(IOException::class)
    fun writeBoolean(v: Boolean): PacketByteBuf {
        out.writeBoolean(v)
        return this
    }

    @Throws(IOException::class)
    fun writeDouble(v: Double): PacketByteBuf {
        out.writeDouble(v)
        return this
    }

    @Throws(IOException::class)
    fun writeShort(v: Short): PacketByteBuf {
        out.writeShort(v.toInt())
        return this
    }

    @Throws(IOException::class)
    fun writeInt(v: Int): PacketByteBuf {
        out.writeInt(v)
        return this
    }

    @Throws(IOException::class)
    fun writeByte(v: Byte): PacketByteBuf {
        out.writeByte(v.toInt())
        return this
    }

    @Throws(IOException::class)
    fun writeString(s: String): PacketByteBuf {
        var len = s.length
        while (true) {
            if (len and -0x80 == 0) {
                out.writeByte(len)
                break
            }
            out.writeByte(len and 0x7F or 0x80)
            len = len ushr 7
        }
        out.writeBytes(s)
        return this
    }

    fun toBytes(): ByteArray {
        return byteArrayOutputStream.toByteArray()
    }
}

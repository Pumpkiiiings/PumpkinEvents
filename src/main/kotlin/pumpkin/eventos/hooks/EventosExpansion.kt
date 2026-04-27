package pumpkin.eventos.hooks

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import pumpkin.eventos.PumpkinEventos
import pumpkin.eventos.games.jalarcuerda.EquipoCuerda
import pumpkin.eventos.games.jalarcuerda.JalarCuerda
import pumpkin.eventos.games.miniwalls.MiniWalls
import pumpkin.eventos.games.miniwalls.MwTeam
import pumpkin.eventos.games.skywars.Skywars

/**
 * Expansión de PlaceholderAPI para PumpkinEventos.
 *
 * Placeholders:
 *  %eventos_team% — Nombre del equipo del jugador coloreado.
 *                    El equipo propio aparece en VERDE, los demás en ROJO.
 *                    Devuelve "" si el jugador no está en un modo con equipos.
 */
class EventosExpansion(private val plugin: PumpkinEventos) : PlaceholderExpansion() {

    override fun getIdentifier() = "eventos"
    override fun getAuthor() = "PumpkinEventos"
    override fun getVersion() = "1.0"
    override fun persist() = true  // La expansión sobrevive a /papi reload

    override fun onPlaceholderRequest(player: Player?, params: String): String {
        if (player == null) return ""

        return when (params.lowercase()) {
            "team" -> resolveTeam(player)
            else   -> ""
        }
    }

    /**
     * Retorna el nombre del equipo del jugador.
     * - Si el jugador está en su equipo: color verde
     * - Ejemplo de output para MiniWalls: "<#39FF14><b>Rojo</b></#39FF14>"
     */
    private fun resolveTeam(player: Player): String {
        val game = plugin.eventManager.currentGame ?: return ""
        if (!game.isRunning) return ""

        return when (game) {

            // ─── MiniWalls ────────────────────────────────────────────────
            is MiniWalls -> {
                val myTeam: MwTeam = game.playerTeam[player] ?: return ""
                "${myTeam.chatColor}<b>${myTeam.displayName}</b>"
            }

            // ─── Jalar la Cuerda ──────────────────────────────────────────
            is JalarCuerda -> {
                val myTeam: EquipoCuerda = game.playerTeam[player] ?: return ""
                "${myTeam.color}<b>${myTeam.nombre}</b>"
            }

            // ─── Skywars Duos ─────────────────────────────────────────────
            is Skywars -> {
                if (!game.isDuos) return ""
                val myTeamId = game.playerTeam[player] ?: return ""
                // Buscar compañeros de equipo
                val teammates = game.players.filter { game.playerTeam[it] == myTeamId && it != player }
                if (teammates.isEmpty()) {
                    "<green><b>Equipo $myTeamId</b></green>"
                } else {
                    "<green><b>Equipo con ${teammates.joinToString(", ") { it.name }}</b></green>"
                }
            }

            else -> ""
        }
    }
}

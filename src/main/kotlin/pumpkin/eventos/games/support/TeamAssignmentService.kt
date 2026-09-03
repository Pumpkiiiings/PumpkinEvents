package pumpkin.eventos.games.support

import org.bukkit.entity.Player

data class TeamAssignment(
    val teamId: Int,
    val members: List<Player>
)

/** Crea equipos de dos conservando el orden de jugadores y la numeración existente. */
object TeamAssignmentService {
    fun pair(players: List<Player>, firstTeamId: Int): List<TeamAssignment> =
        players.chunked(2).mapIndexed { index, members ->
            TeamAssignment(firstTeamId + index, members)
        }
}

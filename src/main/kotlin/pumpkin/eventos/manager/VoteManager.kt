package pumpkin.eventos.manager

import pumpkin.eventos.games.EventGame
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class VoteManager {
    private val votes = ConcurrentHashMap<UUID, String>()

    fun addVote(uuid: UUID, gameId: String) {
        votes[uuid] = gameId
    }

    fun getWinningGame(games: Map<String, EventGame>): String? {
        if (games.isEmpty()) return null

        if (votes.isEmpty()) return games.keys.random()

        val tally = votes.values
            .filter { games.containsKey(it) }
            .groupingBy { it }
            .eachCount()

        if (tally.isEmpty()) return games.keys.random()

        val maxVotes = tally.maxOf { it.value }
        val winners = tally.filterValues { it == maxVotes }.keys

        return winners.random()
    }

    fun getVotesForGame(gameId: String): Int {
        if (votes.isEmpty()) return 0
        return votes.values.count { it.equals(gameId, ignoreCase = true) }
    }

    fun resetVotes() { votes.clear() }
    fun hasVoted(uuid: UUID): Boolean = votes.containsKey(uuid)
    fun removeVote(uuid: UUID) { votes.remove(uuid) }
}

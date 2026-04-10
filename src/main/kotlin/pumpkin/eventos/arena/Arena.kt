package pumpkin.eventos.arena

import org.bukkit.Location

class Arena(val id: String, val type: String) {
    var slimeWorldName: String? = id // Asumimos que el mundo Slime se llama igual que la arena
    var centerLocation: Location? = null
    val spawnPoints: MutableList<Location> = mutableListOf()

    // --- NUEVAS VARIABLES PARA MINIJUEGOS (Ej: Duelo Final) ---
    var gradas: Location? = null
    var dueloA: Location? = null
    var dueloB: Location? = null

    fun addSpawnPoint(loc: Location) {
        if (loc !in spawnPoints) {
            spawnPoints.add(loc)
        }
    }
}

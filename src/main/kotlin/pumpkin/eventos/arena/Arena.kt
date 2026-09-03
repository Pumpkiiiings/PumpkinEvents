package pumpkin.eventos.arena

import org.bukkit.Location

class Arena(val id: String, val type: String) {
    /** `slime` conserva el flujo histórico; `arenaapi` instancia un .schem temporal. */
    var worldProvider: String = "slime"
    var slimeWorldName: String? = id
    var arenaApiTemplateId: String? = null
    var centerLocation: Location? = null
    val spawnPoints: MutableList<Location> = mutableListOf()

    var gradas: Location? = null
    var dueloA: Location? = null
    var dueloB: Location? = null

    // --- NUEVO: SILLAS ---
    val chairs: MutableList<Location> = mutableListOf()

    fun addSpawnPoint(loc: Location) {
        if (loc !in spawnPoints) {
            spawnPoints.add(loc)
        }
    }

    fun addChair(loc: Location) {
        if (loc !in chairs) {
            chairs.add(loc)
        }
    }
}

package pumpkin.eventos

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path

class ResourceContractTest {

    @Test
    fun `all resources copied during startup are packaged`() {
        val requiredResources = listOf(
            "arenas.yml",
            "chat-format.yml",
            "messages.yml",
            "scoreboards.yml",
            "menus/miniwalls.yml",
            "menus/voteskywars.yml",
            "menus/team-selection.yml",
            "menus/iceboat.yml",
            "menus/parkour.yml",
            "menus/simondice.yml"
        )

        requiredResources.forEach { path ->
            assertNotNull(javaClass.classLoader.getResource(path), "Missing packaged resource: $path")
        }
    }

    @Test
    fun `runtime map dependency is declared as required`() {
        val descriptor = assertNotNull(javaClass.classLoader.getResource("plugin.yml")).readText()
        val requiredSection = descriptor.substringAfter("depend:").substringBefore("softdepend:")

        assertContains(requiredSection, "AdvancedSlimePaper")
    }

    @Test
    fun `messages yaml has no duplicate mapping paths`() {
        val lines = assertNotNull(javaClass.classLoader.getResource("messages.yml")).readText().lines()
        val parents = mutableListOf<Pair<Int, String>>()
        val paths = mutableSetOf<String>()
        val duplicates = mutableSetOf<String>()
        val keyPattern = Regex("^(\\s*)([^#\\s][^:]*):(?:\\s*(.*))?$")

        lines.forEach { line ->
            val match = keyPattern.matchEntire(line) ?: return@forEach
            val indent = match.groupValues[1].length
            val key = match.groupValues[2].trim()
            if (key.startsWith("-")) return@forEach

            while (parents.lastOrNull()?.first?.let { it >= indent } == true) parents.removeLast()
            val path = (parents.map { it.second } + key).joinToString(".")
            if (!paths.add(path)) duplicates += path

            if (match.groupValues[3].isBlank()) parents += indent to key
        }

        assertTrue(duplicates.isEmpty(), "Duplicate YAML paths: ${duplicates.sorted()}")
    }

    @Test
    fun `known listeners do not register themselves and get registered twice`() {
        val listeners = listOf(
            "manager/PuntajeHoloManager.kt",
            "games/iceboat/IceBoatListener.kt",
            "games/skywars/SkywarsListener.kt",
            "games/skywars/SkywarsTntFireballListener.kt"
        )
        val sourceRoot = Path.of("src/main/kotlin/pumpkin/eventos")

        listeners.forEach { relativePath ->
            val source = Files.readString(sourceRoot.resolve(relativePath))
            assertTrue(
                "registerEvents(this, plugin)" !in source,
                "$relativePath must be registered only by PumpkinEventos"
            )
        }
    }
}

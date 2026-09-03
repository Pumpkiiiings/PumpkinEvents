package pumpkin.eventos

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureContractTest {
    private val sourceRoot = Path.of("src/main/kotlin/pumpkin/eventos")

    @Test
    fun `games never mutate the global current game directly`() {
        val offenders = kotlinSources(sourceRoot.resolve("games"))
            .filter { "currentGame = null" in it.readText() }
            .map { it.fileName.toString() }

        assertTrue(offenders.isEmpty(), "Only EventManager may clear currentGame: $offenders")
    }

    @Test
    fun `legacy string titles are not used`() {
        val offenders = kotlinSources(sourceRoot)
            .filter { "sendTitle(" in it.readText() }
            .map { it.fileName.toString() }

        assertTrue(offenders.isEmpty(), "Use Adventure titles backed by messages.yml: $offenders")
    }

    @Test
    fun `commands and global listeners contain no direct MiniMessage literals`() {
        val directLiteral = Regex("(?:messageManager\\.parse|\\bmm\\.parse)\\(\\s*\\\"")
        val roots = listOf(sourceRoot.resolve("commands"), sourceRoot.resolve("listeners"))
        val offenders = roots.flatMap(::kotlinSources)
            .filter { directLiteral.containsMatchIn(it.readText()) }
            .map { it.fileName.toString() }

        assertTrue(offenders.isEmpty(), "Move user-facing text to messages.yml: $offenders")
    }

    private fun kotlinSources(root: Path): List<Path> =
        Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) && it.extension == "kt" }.toList() }
}

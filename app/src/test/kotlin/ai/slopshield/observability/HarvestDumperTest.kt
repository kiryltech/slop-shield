package ai.slopshield.observability

import ai.slopshield.core.HarvestComplete
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the [HarvestDumper] debug component.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HarvestDumperTest {

    private val testDumpPath = "/tmp/slop-shield-test-${System.currentTimeMillis()}"
    private val dumpDir = File(testDumpPath)

    /**
     * Configures the system property to point to the test directory.
     */
    @BeforeTest
    fun setup() {
        System.setProperty("slopshield.dump.path", testDumpPath)
    }

    /**
     * Cleans up the dumped files.
     */
    @AfterTest
    fun tearDown() {
        dumpDir.deleteRecursively()
        System.clearProperty("slopshield.dump.path")
    }

    /**
     * Verifies that the dumper writes the clean text to disk.
     */
    @Test
    fun `test dumper writes clean text to disk`() = runTest {
        val dumper = HarvestDumper()
        val event = HarvestComplete(
            id = "test-1",
            cleanText = "Scraped Content",
            success = true
        )

        dumper.onEvent(event)

        val stdoutFile = File(dumpDir, "test-1.stdout.md")

        assertTrue(stdoutFile.exists(), "stdout file should exist")
        assertEquals("Scraped Content", stdoutFile.readText())
    }
}

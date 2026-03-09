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
     * Verifies that the dumper writes both stdout (clean text) and stderr (error text) to disk.
     */
    @Test
    fun `test dumper writes clean and error text to disk`() = runTest {
        val dumper = HarvestDumper()
        val event = HarvestComplete(
            storyId = "test-1",
            cleanText = "Scraped Content",
            errorText = "Some Error",
            exitCode = 0
        )

        dumper.onEvent(event)

        val stdoutFile = File(dumpDir, "test-1.stdout.md")
        val stderrFile = File(dumpDir, "test-1.stderr.log")

        assertTrue(stdoutFile.exists(), "stdout file should exist")
        assertTrue(stderrFile.exists(), "stderr file should exist")
        assertEquals("Scraped Content", stdoutFile.readText())
        assertEquals("Some Error", stderrFile.readText())
    }

    /**
     * Verifies that the dumper does not create an error log file if the error text is blank.
     */
    @Test
    fun `test dumper skips stderr if empty`() = runTest {
        val dumper = HarvestDumper()
        val event = HarvestComplete(
            storyId = "test-2",
            cleanText = "Content Only",
            errorText = "  ",
            exitCode = 0
        )

        dumper.onEvent(event)

        val stdoutFile = File(dumpDir, "test-2.stdout.md")
        val stderrFile = File(dumpDir, "test-2.stderr.log")

        assertTrue(stdoutFile.exists())
        assertEquals("Content Only", stdoutFile.readText())
        assertTrue(!stderrFile.exists(), "stderr file should NOT exist for blank error text")
    }
}

package ai.slopshield.memory

import ai.slopshield.core.ContextRequest
import ai.slopshield.core.ContextResponse
import ai.slopshield.core.SlopEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the [MemoryService] checking file loading and context provisioning.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryServiceTest {

    private val testContextPath = "/tmp/slop-shield-context-test"
    private val contextDir = File(testContextPath)

    /**
     * Creates a temporary directory for context files.
     */
    @BeforeTest
    fun setup() {
        contextDir.mkdirs()
    }

    /**
     * Cleans up the temporary context directory.
     */
    @AfterTest
    fun tearDown() {
        contextDir.deleteRecursively()
    }

    /**
     * Verifies that `loadContext` aggregates files correctly and prioritizes `CONTEXT.md`.
     */
    @Test
    fun `test loadContext prioritizes CONTEXT md and includes other files`() {
        File(contextDir, "CONTEXT.md").writeText("Global Instructions")
        File(contextDir, "article.md").writeText("My Article Content")
        File(contextDir, "notes.txt").writeText("Some Notes")
        File(contextDir, "ignored.exe").writeText("Binary")

        val service = MemoryService(MutableSharedFlow(), testContextPath)
        val context = service.loadContext()

        assertTrue(context.contains("#### INSTRUCTIONS (from CONTEXT.md) ####"))
        assertTrue(context.contains("Global Instructions"))
        assertTrue(context.contains("--- Source: article.md ---"))
        assertTrue(context.contains("My Article Content"))
        assertTrue(context.contains("--- Source: notes.txt ---"))
        assertTrue(context.contains("Some Notes"))
        assertTrue(!context.contains("ignored.exe"), "Should not include unsupported extensions")
        
        // Check order (very basic check)
        val instructionIndex = context.indexOf("Global Instructions")
        val articleIndex = context.indexOf("My Article Content")
        assertTrue(instructionIndex < articleIndex, "CONTEXT.md should come before other writings")
    }

    /**
     * Verifies that the service emits a context response upon receiving a context request.
     */
    @Test
    fun `test reacts to ContextRequest`() = runTest {
        File(contextDir, "CONTEXT.md").writeText("Instructions")
        
        val eventStream = MutableSharedFlow<SlopEvent>(replay = 64)
        val service = MemoryService(eventStream, testContextPath)
        service.start()

        service.onEvent(ContextRequest())

        val responses = eventStream
            .filterIsInstance<ContextResponse>()
            .take(1)
            .toList()

        assertEquals(1, responses.size)
        assertTrue(responses[0].content.contains("Instructions"))
    }
}

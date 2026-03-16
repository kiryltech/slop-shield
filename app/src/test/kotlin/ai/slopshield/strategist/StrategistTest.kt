package ai.slopshield.strategist

import ai.slopshield.core.*
import ai.slopshield.core.MockAiService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.mapdb.DBMaker
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the [Strategist] domain service.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StrategistTest {

    private lateinit var repository: StoryRepository
    private lateinit var aiService: MockAiService
    private lateinit var eventStream: MutableSharedFlow<SlopEvent>

    /**
     * Initializes the repository and event stream for testing.
     */
    @BeforeTest
    fun setup() {
        val db = DBMaker.memoryDB().make()
        repository = StoryRepository(db)
        eventStream = MutableSharedFlow(replay = 64)
    }

    /**
     * Cleans up repository resources.
     */
    @AfterTest
    fun tearDown() {
        repository.close()
    }

    /**
     * Verifies that the Strategist performs deep analysis for valid categories and emits a correct event.
     */
    @Test
    fun `test strategist performs deep analysis and emits event`() = runTest {
        // Setup story
        val story = Story("test-1", "Test Title", "http://test.com", cleanText = "Story content")
        repository.upsert(story)

        // Mock AI result
        val mockJson = """
            {
                "mms": 8, "sa": 7, "sd": 9, "d": 6, 
                "alignment": "OPPOSITE_VIEW", 
                "hypeRisk": "LOW", 
                "sparringNote": "Cynical note"
            }
        """.trimIndent()
        aiService = MockAiService(AiResult(mockJson, "", 0))

        val strategist = Strategist(this, eventStream, eventStream, aiService, repository)
        strategist.start()

        // 1. Provide context
        eventStream.emit(ContextResponse("My personal context"))

        // 2. Trigger analysis
        eventStream.emit(StoryCategorized("test-1", StoryCategory.WRITING, "Reasoning"))

        // 3. Verify AnalysisComplete event
        val results = eventStream
            .filterIsInstance<AnalysisComplete>()
            .take(1)
            .toList()

        assertEquals(1, results.size)
        val result = results[0]
        assertEquals("test-1", result.id)
        assertEquals(8, result.mms)
        assertEquals(7, result.sa)
        assertEquals(Alignment.OPPOSITE_VIEW, result.alignment)
        assertEquals(HypeRisk.LOW, result.hypeRisk)
        assertEquals("Cynical note", result.sparringNote)
        assertEquals(7.5, result.totalScore)

        strategist.stop()
    }

    /**
     * Verifies that the Strategist skips deep analysis for low-signal categories.
     */
    @Test
    fun `test strategist skips low-signal categories`() = runTest {
        aiService = MockAiService(AiResult("{}", "", 0))
        val strategist = Strategist(this, eventStream, eventStream, aiService, repository)
        strategist.start()

        // Trigger with SOURCE category (should be skipped)
        eventStream.emit(StoryCategorized("test-2", StoryCategory.SOURCE, "Reasoning"))

        // Use a short timeout to verify NO event is emitted
        assertTrue(eventStream.replayCache.filterIsInstance<AnalysisComplete>().isEmpty())

        strategist.stop()
    }
}

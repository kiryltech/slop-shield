package ai.slopshield.strategist

import ai.slopshield.core.AiResult
import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.MockAiService
import ai.slopshield.core.StoryCategorized
import ai.slopshield.core.StoryCategory
import ai.slopshield.core.SlopEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the [Categorizer] domain service.
 * Ensures that categorization logic processes successfully harvested stories,
 * handles bad outputs from the AI correctly, and emits valid categorization events.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategorizerTest {

    /**
     * Verifies the standard successful flow: the AI returns a valid category,
     * and the Categorizer correctly emits a [StoryCategorized] event.
     */
    @Test
    fun `test categorizer reacts to HarvestComplete and emits StoryCategorized`() = runTest {
        val storyId = "123"
        val cleanText = "This is a blog post about Kotlin."
        val mockAiResponse = """{"category": "WRITING", "reasoning": "It looks like an article."}"""

        val mockAIService = MockAiService(AiResult(mockAiResponse, "", 0))

        val eventStream = MutableSharedFlow<SlopEvent>(replay = 64)
        val categorizer = Categorizer(
            collector = eventStream,
            aiService = mockAIService
        )

        backgroundScope.launch {
            eventStream
                .filterIsInstance<HarvestComplete>()
                .collect { categorizer.onEvent(it) }
        }

        // Emit harvest complete event
        eventStream.emit(
            HarvestComplete(storyId = storyId, cleanText = cleanText, errorText = "", exitCode = 0)
        )

        val categorizedEvents = eventStream
            .filterIsInstance<StoryCategorized>()
            .take(1)
            .toList()

        assertEquals(1, categorizedEvents.size)
        assertEquals(storyId, categorizedEvents[0].storyId)
        assertEquals(StoryCategory.WRITING, categorizedEvents[0].category)
        assertEquals("It looks like an article.", categorizedEvents[0].reasoning)
    }

    /**
     * Verifies that if the AI returns a category string not present in the
     * [StoryCategory] enum, it defaults to [StoryCategory.UNKNOWN] gracefully.
     */
    @Test
    fun `test categorizer handles unknown categories gracefully`() = runTest {
        val storyId = "456"
        val cleanText = "Something weird."
        val mockAiResponse = """{"category": "INVALID_CAT", "reasoning": "I don't know what this is."}"""

        val mockAIService = MockAiService(
            AiResult(mockAiResponse, "", 0)
        )

        val eventStream = MutableSharedFlow<SlopEvent>(replay = 64)
        val categorizer = Categorizer(
            collector = eventStream,
            aiService = mockAIService
        )

        backgroundScope.launch {
            eventStream
                .filterIsInstance<HarvestComplete>()
                .collect { categorizer.onEvent(it) }
        }

        // Emit harvest complete event
        eventStream.emit(
            HarvestComplete(storyId = storyId, cleanText = cleanText, errorText = "", exitCode = 0)
        )

        val categorizedEvents = eventStream
            .filterIsInstance<StoryCategorized>()
            .take(1)
            .toList()

        assertEquals(1, categorizedEvents.size)
        assertEquals(StoryCategory.UNKNOWN, categorizedEvents[0].category)
    }

    /**
     * Verifies that JSON output wrapped in markdown code blocks (e.g., ```json ... ```)
     * is correctly sanitized before parsing.
     */
    @Test
    fun `test categorizer sanitizes markdown wrapped json`() = runTest {
        val storyId = "789"
        val cleanText = "Markdown wrap test."
        val mockAiResponse = """
            ```json
            {"category": "DEMO", "reasoning": "Wrapped in code blocks."}
            ```
        """.trimIndent()

        val mockAIService = MockAiService(
            AiResult(mockAiResponse, "", 0)
        )

        val eventStream = MutableSharedFlow<SlopEvent>(replay = 64)
        val categorizer = Categorizer(
            collector = eventStream,
            aiService = mockAIService
        )

        backgroundScope.launch {
            eventStream
                .filterIsInstance<HarvestComplete>()
                .collect { categorizer.onEvent(it) }
        }

        // Emit harvest complete event
        eventStream.emit(
            HarvestComplete(storyId = storyId, cleanText = cleanText, errorText = "", exitCode = 0)
        )

        val categorizedEvents = eventStream
            .filterIsInstance<StoryCategorized>()
            .take(1)
            .toList()

        assertEquals(1, categorizedEvents.size)
        assertEquals(StoryCategory.DEMO, categorizedEvents[0].category)
        assertEquals("Wrapped in code blocks.", categorizedEvents[0].reasoning)
    }

    /**
     * Verifies that a valid SOURCE categorization produces the correct enum type.
     */
    @Test
    fun `test categorizer identifies source category`() = runTest {
        val storyId = "abc"
        val cleanText = "Repository containing the source code for a new Kotlin library."
        val mockAiResponse = """{"category": "SOURCE", "reasoning": "It's a code repository."}"""

        val mockAIService = MockAiService(
            mockResult = AiResult(mockAiResponse, "", 0)
        )

        val eventStream = MutableSharedFlow<SlopEvent>(replay = 64)
        val categorizer = Categorizer(
            collector = eventStream,
            aiService = mockAIService
        )

        backgroundScope.launch {
            eventStream
                .filterIsInstance<HarvestComplete>()
                .collect { categorizer.onEvent(it) }
        }

        // Emit harvest complete event
        eventStream.emit(
            HarvestComplete(storyId = storyId, cleanText = cleanText, errorText = "", exitCode = 0)
        )

        val categorizedEvents = eventStream
            .filterIsInstance<StoryCategorized>()
            .take(1)
            .toList()

        assertEquals(1, categorizedEvents.size)
        assertEquals(StoryCategory.SOURCE, categorizedEvents[0].category)
    }
}

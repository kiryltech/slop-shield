package ai.slopshield.strategist

import ai.slopshield.core.AIResult
import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.InternalDomainEventStream
import ai.slopshield.core.StoryCategorized
import ai.slopshield.core.StoryCategory
import ai.slopshield.harvester.MockAIService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CategorizerTest {

    @BeforeTest
    fun setup() {
        InternalDomainEventStream.reset()
    }

    @Test
    fun `test categorizer reacts to HarvestComplete and emits StoryCategorized`() = runTest {
        val storyId = "123"
        val cleanText = "This is a blog post about Kotlin."
        val mockAiResponse = """{"category": "WRITING", "reasoning": "It looks like an article."}"""

        val mockAIService = MockAIService().apply {
            mockResult = AIResult(mockAiResponse, "", 0)
        }

        val categorizer = Categorizer(
            scope = backgroundScope,
            eventStream = InternalDomainEventStream,
            aiService = mockAIService
        )
        categorizer.start()

        // Emit harvest complete event
        InternalDomainEventStream.emit(
            HarvestComplete(storyId = storyId, cleanText = cleanText, errorText = "", exitCode = 0)
        )

        val categorizedEvents = InternalDomainEventStream.events
            .filterIsInstance<StoryCategorized>()
            .take(1)
            .toList()

        assertEquals(1, categorizedEvents.size)
        assertEquals(storyId, categorizedEvents[0].storyId)
        assertEquals(StoryCategory.WRITING, categorizedEvents[0].category)
        assertEquals("It looks like an article.", categorizedEvents[0].reasoning)
    }

    @Test
    fun `test categorizer handles unknown categories gracefully`() = runTest {
        val storyId = "456"
        val cleanText = "Something weird."
        val mockAiResponse = """{"category": "INVALID_CAT", "reasoning": "I don't know what this is."}"""

        val mockAIService = MockAIService().apply {
            mockResult = AIResult(mockAiResponse, "", 0)
        }

        val categorizer = Categorizer(
            scope = backgroundScope,
            eventStream = InternalDomainEventStream,
            aiService = mockAIService
        )
        categorizer.start()

        // Emit harvest complete event
        InternalDomainEventStream.emit(
            HarvestComplete(storyId = storyId, cleanText = cleanText, errorText = "", exitCode = 0)
        )

        val categorizedEvents = InternalDomainEventStream.events
            .filterIsInstance<StoryCategorized>()
            .take(1)
            .toList()

        assertEquals(1, categorizedEvents.size)
        assertEquals(StoryCategory.UNKNOWN, categorizedEvents[0].category)
    }

    @Test
    fun `test categorizer sanitizes markdown wrapped json`() = runTest {
        val storyId = "789"
        val cleanText = "Markdown wrap test."
        val mockAiResponse = """
            ```json
            {"category": "DEMO", "reasoning": "Wrapped in code blocks."}
            ```
        """.trimIndent()

        val mockAIService = MockAIService().apply {
            mockResult = AIResult(mockAiResponse, "", 0)
        }

        val categorizer = Categorizer(
            scope = backgroundScope,
            eventStream = InternalDomainEventStream,
            aiService = mockAIService
        )
        categorizer.start()

        // Emit harvest complete event
        InternalDomainEventStream.emit(
            HarvestComplete(storyId = storyId, cleanText = cleanText, errorText = "", exitCode = 0)
        )

        val categorizedEvents = InternalDomainEventStream.events
            .filterIsInstance<StoryCategorized>()
            .take(1)
            .toList()

        assertEquals(1, categorizedEvents.size)
        assertEquals(StoryCategory.DEMO, categorizedEvents[0].category)
        assertEquals("Wrapped in code blocks.", categorizedEvents[0].reasoning)
    }

    @Test
    fun `test categorizer identifies source category`() = runTest {
        val storyId = "abc"
        val cleanText = "Repository containing the source code for a new Kotlin library."
        val mockAiResponse = """{"category": "SOURCE", "reasoning": "It's a code repository."}"""

        val mockAIService = MockAIService().apply {
            mockResult = AIResult(mockAiResponse, "", 0)
        }

        val categorizer = Categorizer(
            scope = backgroundScope,
            eventStream = InternalDomainEventStream,
            aiService = mockAIService
        )
        categorizer.start()

        // Emit harvest complete event
        InternalDomainEventStream.emit(
            HarvestComplete(storyId = storyId, cleanText = cleanText, errorText = "", exitCode = 0)
        )

        val categorizedEvents = InternalDomainEventStream.events
            .filterIsInstance<StoryCategorized>()
            .take(1)
            .toList()

        assertEquals(1, categorizedEvents.size)
        assertEquals(StoryCategory.SOURCE, categorizedEvents[0].category)
    }
}

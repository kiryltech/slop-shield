package ai.slopshield.harvester

import ai.slopshield.core.AIResult
import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.SlopEvent
import ai.slopshield.core.StoryDiscovered
import ai.slopshield.core.AIService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A mock implementation of [AIService] to avoid invoking real AI inference during testing.
 * Captures the input to verify the prompt context was assembled properly.
 */
class MockAIService : AIService(maxParallelTasks = 1) {
    /** The predefined result that will be returned upon process execution. */
    var mockResult: AIResult = AIResult("", "", 0)
    /** The input string that was captured during the process call. */
    var capturedInput: String = ""

    override suspend fun process(prompt: String, input: String, timeoutSeconds: Long): AIResult {
        capturedInput = input
        return mockResult
    }
}

/**
 * Tests for the [Harvester] domain service, verifying web scraping and AI text extraction logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HarvesterTest {

    /**
     * Verifies that discovering a story correctly triggers an HTTP fetch, pipes the HTML
     * into the AI service, and emits a [HarvestComplete] event with the cleaned text.
     */
    @Test
    fun `test harvester reacts to StoryDiscovered and emits HarvestComplete`() = runTest {
        val storyId = "123"
        val storyTitle = "Test Story"
        val storyUrl = "http://example.com"
        val rawHtml = "<html><body>Hello World</body></html>"
        val expectedText = "Cleaned content from Gemini"

        val mockEngine = MockEngine { request ->
            if (request.url.toString() == storyUrl) {
                respond(
                    content = rawHtml,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html")
                )
            } else {
                respondError(HttpStatusCode.NotFound)
            }
        }
        val httpClient = HttpClient(mockEngine)
        
        val mockAIService = MockAIService().apply {
            mockResult = AIResult(expectedText, "", 0)
        }

        val eventStream = MutableSharedFlow<SlopEvent>(replay = 64)
        val harvester = Harvester(
            httpClient = httpClient,
            collector = eventStream,
            aiService = mockAIService
        )
        
        // Manual event processing for unit test (avoiding EventCoordinator complexity)
        backgroundScope.launch {
            eventStream
                .filterIsInstance<StoryDiscovered>()
                .collect { harvester.onEvent(it) }
        }

        // Emit discovery event
        eventStream.emit(
            StoryDiscovered(id = storyId, title = storyTitle, url = storyUrl)
        )

        val harvestEvents = eventStream
            .filterIsInstance<HarvestComplete>()
            .take(1)
            .toList()

        assertEquals(1, harvestEvents.size)
        assertEquals(storyId, harvestEvents[0].storyId)
        assertEquals(expectedText, harvestEvents[0].cleanText)
        assertEquals(0, harvestEvents[0].exitCode)
        assertEquals(rawHtml, mockAIService.capturedInput)
    }
}

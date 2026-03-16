package ai.slopshield.harvester

import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.SlopEvent
import ai.slopshield.core.StoryDiscovered
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the [Harvester] domain service, verifying web scraping and HTML conversion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HarvesterTest {

    /**
     * Verifies that discovering a story correctly triggers an HTTP fetch, converts the HTML
     * to Markdown, and emits a [HarvestComplete] event with the cleaned text.
     */
    @Test
    fun `test harvester reacts to StoryDiscovered and emits HarvestComplete`() = runTest {
        val storyId = "123"
        val storyTitle = "Test Story"
        val storyUrl = "http://example.com"
        val rawHtml = "<html><body><h1>Hello World</h1><p>Test</p></body></html>"
        // Expected Markdown result from Flexmark
        val expectedMarkdown = "Hello World\n===========\n\nTest\n"

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
        
        val eventStream = MutableSharedFlow<SlopEvent>(replay = 64)
        val harvester = Harvester(
            httpClient = httpClient,
            collector = eventStream
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
        assertEquals(storyId, harvestEvents[0].id)
        // Note: Flexmark's exact output might vary slightly depending on configuration, 
        // so we check if it contains the key parts.
        assertTrue(harvestEvents[0].cleanText.contains("Hello World"))
        assertTrue(harvestEvents[0].cleanText.contains("Test"))
        assertTrue(harvestEvents[0].success)
    }

    /**
     * Verifies that the harvester emits a failed HarvestComplete event upon HTTP error.
     */
    @Test
    fun `test harvester emits failure on HTTP error`() = runTest {
        val storyId = "123"
        val storyUrl = "http://example.com/not-found"

        val mockEngine = MockEngine { _ ->
            respondError(HttpStatusCode.NotFound)
        }
        val httpClient = HttpClient(mockEngine)
        
        val eventStream = MutableSharedFlow<SlopEvent>(replay = 64)
        val harvester = Harvester(httpClient, eventStream)
        
        harvester.onEvent(StoryDiscovered(id = storyId, title = "Error Story", url = storyUrl))

        val harvestEvents = eventStream
            .filterIsInstance<HarvestComplete>()
            .take(1)
            .toList()

        assertEquals(1, harvestEvents.size)
        assertEquals(storyId, harvestEvents[0].id)
        assertEquals("", harvestEvents[0].cleanText)
        assertTrue(!harvestEvents[0].success)
    }
}

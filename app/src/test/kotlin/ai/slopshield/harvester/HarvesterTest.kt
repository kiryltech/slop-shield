package ai.slopshield.harvester

import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.InternalDomainEventStream
import ai.slopshield.core.StoryDiscovered
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class HarvesterTest {

    @BeforeTest
    fun setup() {
        InternalDomainEventStream.reset()
    }

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

        val harvester = Harvester(
            scope = backgroundScope,
            httpClient = httpClient,
            eventStream = InternalDomainEventStream,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            scraper = { content -> 
                if (content == rawHtml) ScraperResult(expectedText, "", 0) 
                else ScraperResult("", "Error", 1) 
            }
        )
        harvester.start()

        // Emit discovery event
        InternalDomainEventStream.emit(
            StoryDiscovered(id = storyId, title = storyTitle, url = storyUrl)
        )

        val harvestEvents = InternalDomainEventStream.events
            .filterIsInstance<HarvestComplete>()
            .take(1)
            .toList()

        assertEquals(1, harvestEvents.size)
        assertEquals(storyId, harvestEvents[0].storyId)
        assertEquals(expectedText, harvestEvents[0].cleanText)
        assertEquals(0, harvestEvents[0].exitCode)
    }
}

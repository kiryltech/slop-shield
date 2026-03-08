package ai.slopshield.scout

import ai.slopshield.core.InternalDomainEventStream
import ai.slopshield.core.StoryDiscovered
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ScoutTest {

    @BeforeTest
    fun setup() {
        InternalDomainEventStream.reset()
    }

    @Test
    fun `test scout discovers new stories`() = runTest {
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            when (path) {
                "/v0/topstories.json" -> {
                    respond(
                        content = "[1, 2]",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/v0/item/1.json" -> {
                    respond(
                        content = """{"id": 1, "title": "Story 1", "url": "http://example.com/1", "type": "story"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/v0/item/2.json" -> {
                    respond(
                        content = """{"id": 2, "title": "Story 2", "url": "http://example.com/2", "type": "story"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val scout = Scout(this, httpClient, { event -> InternalDomainEventStream.emit(event) }, pollInterval = Duration.ofMinutes(15), limit = 2)
        
        // Directly trigger polling
        scout.pollTopStories()
        
        val discoveredEvents = InternalDomainEventStream.events
            .filterIsInstance<StoryDiscovered>()
            .take(2)
            .toList()

        assertEquals(2, discoveredEvents.size, "Should have discovered 2 stories")
        assertEquals("Story 1", discoveredEvents[0].title)
        assertEquals("Story 2", discoveredEvents[1].title)
    }
}

package ai.slopshield.observability

import ai.slopshield.core.ActivityEvent
import ai.slopshield.core.SlopEvent
import ai.slopshield.core.Story
import ai.slopshield.core.StoryRepository
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import org.mapdb.DBMaker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the [WebServiceV2] class to ensure routing and API behavior work as expected.
 */
class WebServiceV2Test {

    /**
     * Verifies that the base path ("/") correctly serves the dashboard UI.
     */
    @Test
    fun `test dashboard loads`() = testApplication {
        val db = DBMaker.memoryDB().make()
        val repository = StoryRepository(db)
        val eventStream = MutableSharedFlow<SlopEvent>()
        val activityStream = MutableSharedFlow<ActivityEvent>()
        
        application {
            val registry = RecentActivityRegistry(this, activityStream)
            val service = WebServiceV2(this, repository, registry, eventStream, activityStream)
            with(service) {
                configureModule()
            }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("SlopShield Dashboard"))
    }

    /**
     * Verifies that the REST API correctly returns the stored stories.
     */
    @Test
    fun `test api returns stories`() = testApplication {
        val db = DBMaker.memoryDB().make()
        val repository = StoryRepository(db)
        val eventStream = MutableSharedFlow<SlopEvent>()
        val activityStream = MutableSharedFlow<ActivityEvent>()
        
        repository.upsert(Story("1", "Test Story", "http://test.com"))

        application {
            val registry = RecentActivityRegistry(this, activityStream)
            val service = WebServiceV2(this, repository, registry, eventStream, activityStream)
            with(service) {
                configureModule()
            }
        }

        val response = client.get("/api/stories")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Test Story"))
    }
}

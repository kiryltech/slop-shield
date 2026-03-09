package ai.slopshield.observability

import ai.slopshield.core.ActivityEvent
import ai.slopshield.core.SlopEvent
import ai.slopshield.core.SlopEventSerializer
import ai.slopshield.core.SlopService
import ai.slopshield.core.SlopServiceLifecycle
import ai.slopshield.core.StoryDiscovered
import ai.slopshield.core.StoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * A modern, interactive Web UI (V2) backend.
 * Serves static UI assets and provides REST + WebSocket endpoints for real-time data.
 *
 * @property scope The coroutine scope for launching broadcast jobs.
 * @property repository The underlying repository to serve the initial state of stories.
 * @property recentActivity The registry for supplying recent historical events.
 * @property eventStream The domain event stream to broadcast via WebSockets.
 * @property activityStream The activity event stream to broadcast via WebSockets.
 * @property port The port number for the embedded Netty server to listen on.
 */
@SlopService
class WebServiceV2(
    private val scope: CoroutineScope,
    private val repository: StoryRepository,
    private val recentActivity: RecentActivityRegistry,
    private val eventStream: MutableSharedFlow<SlopEvent>,
    private val activityStream: MutableSharedFlow<ActivityEvent>,
    private val port: Int = System.getProperty("slopshield.web.v2.port", "8081").toInt()
) : SlopServiceLifecycle {

    private var server: NettyApplicationEngine? = null
    private val sessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())

    /**
     * Starts the embedded Netty server and begins observing streams to broadcast to WebSockets.
     */
    override fun start() {
        logger.info { "WebServiceV2: Starting on port $port..." }

        server = embeddedServer(Netty, port = port) {
            configureModule()
        }.start(wait = false)

        // Broadcast domain events
        scope.launch {
            eventStream.collect { event ->
                broadcast(Json.encodeToString(SlopEventSerializer, event))
            }
        }

        // Broadcast activity events
        scope.launch {
            activityStream.collect { event ->
                // ActivityEvent is a sealed interface, so we can use its serializer
                broadcast(Json.encodeToString(ActivityEvent.serializer(), event))
            }
        }
    }

    /**
     * Broadcasts a JSON string to all active WebSocket sessions.
     *
     * @param json The serialized event to broadcast.
     */
    private fun broadcast(json: String) {
        sessions.forEach { session ->
            try {
                scope.launch {
                    session.send(json)
                }
            } catch (e: Exception) {
                // Session likely closed
            }
        }
    }

    /**
     * Configures the Ktor application module, setting up WebSockets, ContentNegotiation, and Routing.
     * Extracted for testability and clarity.
     */
    fun Application.configureModule() {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(15)
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }

        routing {
            // 1. Serve Static UI
            staticResources("/", "ui", index = "index.html")

            // 2. REST API for initial load
            get("/api/stories") {
                val stories = repository.getAll().sortedByDescending { it.id }.toList()
                call.respond(stories)
            }

            get("/api/activity/recent") {
                call.respond(recentActivity.getRecentEvents())
            }

            post("/api/stories/{id}/reload") {
                val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val story = repository.get(id) ?: return@post call.respond(HttpStatusCode.NotFound)
                
                logger.info { "WebServiceV2: Manual reload requested for story $id" }
                // Re-emit discovery to trigger the whole pipeline
                scope.launch {
                    eventStream.emit(
                        StoryDiscovered(story.id, story.title, story.url)
                    )
                }
                call.respond(HttpStatusCode.Accepted)
            }

            // 3. WebSocket for real-time updates
            webSocket("/ws/events") {
                sessions.add(this)
                try {
                    for (frame in incoming) { /* keep session open */ }
                } finally {
                    sessions.remove(this)
                }
            }
        }
    }

    /**
     * Gracefully stops the web server.
     */
    override fun stop() {
        logger.info { "WebServiceV2: Stopping..." }
        server?.stop(1000, 5000)
    }
}

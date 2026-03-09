package ai.slopshield.observability

import ai.slopshield.core.SlopEvent
import ai.slopshield.core.SlopEventSerializer
import ai.slopshield.core.SlopService
import ai.slopshield.core.SlopServiceLifecycle
import ai.slopshield.core.StoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
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
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * A modern, interactive Web UI (V2) backend.
 * Serves static UI assets and provides REST + WebSocket endpoints for real-time data.
 */
@SlopService
class WebServiceV2(
    private val scope: CoroutineScope,
    private val repository: StoryRepository,
    private val eventStream: SharedFlow<SlopEvent>,
    private val port: Int = System.getProperty("slopshield.web.v2.port", "8081").toInt()
) : SlopServiceLifecycle {

    private var server: NettyApplicationEngine? = null
    private val sessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())

    override fun start() {
        logger.info { "WebServiceV2: Starting on port $port..." }

        server = embeddedServer(Netty, port = port) {
            configureModule()
        }.start(wait = false)

        // Broadcast events to all connected WebSocket sessions
        scope.launch {
            eventStream.collect { event ->
                val json = try {
                    Json.encodeToString(SlopEventSerializer, event)
                } catch (e: Exception) {
                    logger.warn { "WebServiceV2: Failed to serialize event ${event::class.simpleName}: ${e.message}" }
                    null
                }
                
                if (json != null) {
                    sessions.forEach { session ->
                        try {
                            session.send(json)
                        } catch (e: Exception) {
                            // Session likely closed
                        }
                    }
                }
            }
        }
    }

    /**
     * Configures the Ktor application module.
     * Extracted for testability.
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

    override fun stop() {
        logger.info { "WebServiceV2: Stopping..." }
        server?.stop(1000, 5000)
    }
}

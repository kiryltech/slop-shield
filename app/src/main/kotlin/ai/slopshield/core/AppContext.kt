package ai.slopshield.core

import ai.slopshield.scout.Scout
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger {}

/**
 * The application context responsible for IoC, dependency injection, and lifecycle management.
 * It coordinates the initialization and shutdown of all infrastructure and domain services.
 */
class AppContext(private val scope: CoroutineScope) {
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val repository = StoryRepository()
    val aiService = AIService()
    val eventStream = MutableSharedFlow<SlopEvent>(replay = 64)

    private val registry: Map<KClass<*>, Any> = mapOf(
        HttpClient::class to httpClient,
        StoryRepository::class to repository,
        AIService::class to aiService,
        FlowCollector::class to eventStream,
        SharedFlow::class to eventStream,
        CoroutineScope::class to scope
    )

    private val coordinator = EventCoordinator(scope, eventStream, registry)
    private val scout = Scout(scope, httpClient, eventStream)

    fun start() {
        logger.info { "AppContext: Initializing SlopShield core services..." }
        
        // Start orchestration
        coordinator.start()

        // Start active producers
        logger.info { "AppContext: Starting active producers..." }
        scout.start()
    }

    suspend fun stop() {
        logger.info { "AppContext: Shutting down services..." }
        aiService.shutdown()
        httpClient.close()
        repository.close()
    }
}

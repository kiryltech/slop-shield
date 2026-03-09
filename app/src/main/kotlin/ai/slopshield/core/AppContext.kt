package ai.slopshield.core

import ai.slopshield.observability.RecentActivityRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import org.mapdb.DBMaker
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger {}

/**
 * The application context responsible for IoC, dependency injection, and lifecycle management.
 * It coordinates the initialization and shutdown of all infrastructure and domain services.
 * 
 * @property scope The coroutine scope used for all async operations in the app.
 */
class AppContext(scope: CoroutineScope) : AutoCloseable {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /**
     * Persistent repository for storing processed stories.
     */
    val repository = StoryRepository(
        DBMaker
            .fileDB(System.getProperty("slopshield.db.path", "slopshield.db"))
            .transactionEnable()
            .closeOnJvmShutdown()
            .make()
    )
    
    /**
     * The AI execution engine wrapper.
     */
    val aiService = AIService()
    
    /**
     * Main event stream for communication between domain components.
     */
    val eventStream = MutableSharedFlow<SlopEvent>(replay = 64)
    
    /**
     * Activity stream for tracking system and handler execution.
     */
    val activityStream = MutableSharedFlow<ActivityEvent>(replay = 32)
    
    /**
     * Registry holding recent system activities.
     */
    val recentActivity = RecentActivityRegistry(scope, activityStream)

    private val registry: Map<KClass<*>, Any> = mapOf(
        HttpClient::class to httpClient,
        StoryRepository::class to repository,
        AIService::class to aiService,
        RecentActivityRegistry::class to recentActivity,
        CoroutineScope::class to scope
    )

    private val coordinator = EventCoordinator(scope, eventStream, activityStream, registry)

    /**
     * Starts the application context, initializing and wiring all components.
     */
    fun start() {
        logger.info { "AppContext: Initializing SlopShield core services..." }

        // coordinator.start() will discover and start all SlopHandlers and SlopServices
        coordinator.start()
    }

    /**
     * Gracefully shuts down the application context and releases resources.
     */
    override fun close() {
        logger.info { "🛡️ SlopShield is shutting down..." }
        logger.info { "AppContext: Shutting down services..." }
        coordinator.stop()
        aiService.shutdown()
        httpClient.close()
        repository.close()
    }
}

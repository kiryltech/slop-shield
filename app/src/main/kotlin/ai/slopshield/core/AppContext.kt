package ai.slopshield.core

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import org.mapdb.DBMaker
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger {}

/**
 * The application context responsible for IoC, dependency injection, and lifecycle management.
 * It coordinates the initialization and shutdown of all infrastructure and domain services.
 */
class AppContext(scope: CoroutineScope) : AutoCloseable {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val repository = StoryRepository(
        DBMaker
            .fileDB(System.getProperty("slopshield.db.path", "slopshield.db"))
            .transactionEnable()
            .closeOnJvmShutdown()
            .make()
    )
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

    fun start() {
        logger.info { "AppContext: Initializing SlopShield core services..." }

        // coordinator.start() will discover and start all SlopHandlers and SlopServices
        coordinator.start()
    }

    override fun close() {
        logger.info { "🛡️ SlopShield is shutting down..." }
        logger.info { "AppContext: Shutting down services..." }
        coordinator.stop()
        aiService.shutdown()
        httpClient.close()
        repository.close()
    }


}

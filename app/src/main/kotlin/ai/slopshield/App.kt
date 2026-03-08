package ai.slopshield

import ai.slopshield.core.*
import ai.slopshield.harvester.Harvester
import ai.slopshield.scout.Scout
import ai.slopshield.strategist.Categorizer
import ai.slopshield.observability.HarvestDumper
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger {}

class App {
    fun run() = runBlocking {
        logger.info { "🛡️ Project SlopShield: Starting the Domain Event Stream..." }

        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        
        // Infrastructure
        val repository = StoryRepository()
        val aiService = AIService()
        val emit: SlopEmitter = { event -> InternalDomainEventStream.emit(event) }
        
        // Use a SupervisorJob to ensure failure in one domain doesn't cancel others
        val supervisor = SupervisorJob()
        val appScope = CoroutineScope(coroutineContext + supervisor)

        // Dependency Registry for Reflection-based instantiation
        val registry: Map<KClass<*>, Any> = mapOf(
            HttpClient::class to httpClient,
            StoryRepository::class to repository,
            AIService::class to aiService,
            DomainEventStream::class to InternalDomainEventStream,
            Function1::class to emit, // For SlopEmitter (suspend function is Function1 in reflection)
            CoroutineScope::class to appScope
        )

        // Orchestration
        val coordinator = EventCoordinator(appScope, InternalDomainEventStream, registry)
        coordinator.start()

        // Domains that are producers (not just listeners) still need explicit starting
        val scout = Scout(appScope, httpClient, emit)

        logger.info { "🛡️ Starting Active Domain Services..." }
        scout.start()

        logger.info { "🛡️ SlopShield is running. Press Ctrl+C to stop." }
        
        // Wait for the supervisor job children to complete
        supervisor.children.toList().joinAll()
        
        // Cleanup
        aiService.shutdown()
        repository.close()
    }
}

fun main() {
    App().run()
}

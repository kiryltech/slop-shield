package ai.slopshield

import ai.slopshield.core.InternalDomainEventStream
import ai.slopshield.core.AIService
import ai.slopshield.core.StoryRepository
import ai.slopshield.core.StoryProjector
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
        
        // Use a SupervisorJob to ensure failure in one domain doesn't cancel others
        val supervisor = SupervisorJob()
        val appScope = CoroutineScope(coroutineContext + supervisor)

        // Core / State Projection
        val projector = StoryProjector(appScope, InternalDomainEventStream, repository)
        
        // Domains
        val scout = Scout(appScope, httpClient, InternalDomainEventStream)
        val harvester = Harvester(appScope, httpClient, InternalDomainEventStream, aiService)
        val categorizer = Categorizer(appScope, InternalDomainEventStream, aiService)
        
        // Observability
        val dumper = HarvestDumper(appScope, InternalDomainEventStream)

        logger.info { "🛡️ Starting Domain Services..." }
        projector.start()
        dumper.start()
        harvester.start()
        categorizer.start()
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

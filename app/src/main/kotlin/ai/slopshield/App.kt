package ai.slopshield

import ai.slopshield.core.InternalDomainEventStream
import ai.slopshield.harvester.Harvester
import ai.slopshield.scout.Scout
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
        
        // Use a SupervisorJob to ensure failure in one domain doesn't cancel others
        val supervisor = SupervisorJob()
        val appScope = CoroutineScope(coroutineContext + supervisor)

        val scout = Scout(appScope, httpClient, InternalDomainEventStream, pollInterval = Duration.ofMinutes(15))
        val harvester = Harvester(appScope, httpClient, InternalDomainEventStream)
        val dumper = HarvestDumper(appScope, InternalDomainEventStream)

        logger.info { "🛡️ Starting Domain Services..." }
        dumper.start()
        harvester.start()
        scout.start()

        logger.info { "🛡️ SlopShield is running. Press Ctrl+C to stop." }
        
        // Wait for the supervisor job children to complete
        supervisor.children.toList().joinAll()
    }
}

fun main() {
    App().run()
}

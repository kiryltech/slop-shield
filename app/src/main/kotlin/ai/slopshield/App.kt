package ai.slopshield

import ai.slopshield.core.InternalDomainEventStream
import ai.slopshield.harvester.Harvester
import ai.slopshield.scout.Scout
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.time.Duration

class App {
    fun run() = runBlocking {
        println("🛡️ Project SlopShield: Starting the Domain Event Stream...")

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
        val harvester = Harvester(appScope, InternalDomainEventStream)

        println("🛡️ Starting the Scout and Harvester...")
        harvester.start()
        scout.start()

        println("🛡️ SlopShield is running. Press Ctrl+C to stop.")
        
        // Wait for the supervisor job children to complete
        supervisor.children.toList().joinAll()
    }
}

fun main() {
    App().run()
}

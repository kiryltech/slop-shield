package ai.slopshield

import ai.slopshield.core.AppContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*

private val logger = KotlinLogging.logger {}

class App {
    fun run() = runBlocking {
        logger.info { "🛡️ SlopShield is warming up..." }

        val context = AppContext(this)
        context.start()

        logger.info { "🛡️ SlopShield is active. Press Ctrl+C to stop." }
        
        try {
            // Keep the app running until the scope is cancelled
            coroutineContext[Job]?.children?.toList()?.joinAll()
        } finally {
            logger.info { "🛡️ SlopShield is shutting down..." }
            context.stop()
        }
    }
}

fun main() {
    App().run()
}

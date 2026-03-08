package ai.slopshield

import ai.slopshield.core.AppContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import java.io.StringReader

private val logger = KotlinLogging.logger {}

class App {
    fun run() = runBlocking {
        logger.info { "🛡️ SlopShield is warming up..." }

        AppContext(this).use {
            it.start()
            logger.info { "🛡️ SlopShield is active. Press Ctrl+C to stop." }
            // Keep the app running until the scope is cancelled
            coroutineContext[Job]?.children?.toList()?.joinAll()
        }

    }
}

fun main(array: Array<String>) {
    processArgs(array)
    App().run()
}

private fun processArgs(array: Array<String>) {
    if (array.isEmpty()) {
        return
    }
    val props = array.joinToString("\n")
    logger.info { "Properties to load: \n$props" }
    System.getProperties()
        .load(StringReader(props))

}

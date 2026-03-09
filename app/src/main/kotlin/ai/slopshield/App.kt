package ai.slopshield

import ai.slopshield.core.AppContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.io.StringReader

private val logger = KotlinLogging.logger {}

/**
 * The main application class for SlopShield.
 * Responsible for bootstrapping the application context and keeping the process alive.
 */
class App {
    /**
     * Runs the application loop.
     * Initializes [AppContext] and suspends execution until the coroutine scope is cancelled.
     */
    fun run() = runBlocking {
        logger.info { "🛡️ SlopShield is warming up..." }

        AppContext(this).use {
            it.start()
            logger.info { "🛡️ SlopShield is active. Press Ctrl+C to stop." }
            // Keep the app running until the scope is cancelled
            while (coroutineContext.isActive) {
                delay(1000)
            }
        }
    }
}

/**
 * Entry point for the SlopShield application.
 *
 * @param array Command-line arguments passed to the application.
 */
fun main(array: Array<String>) {
    processArgs(array)
    App().run()
}

/**
 * Processes command-line arguments and loads them into system properties.
 *
 * @param array Command-line arguments. Each argument is treated as a property line.
 */
private fun processArgs(array: Array<String>) {
    if (array.isEmpty()) {
        return
    }
    val props = array.joinToString("\n")
    logger.info { "Properties to load: \n$props" }
    System.getProperties()
        .load(StringReader(props))
}

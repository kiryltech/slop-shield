package ai.slopshield.harvester

import ai.slopshield.core.DomainEventStream
import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.StoryDiscovered
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * The Harvester domain service.
 * Listens for [StoryDiscovered] events and extracts clean text using the Gemini CLI.
 */
class Harvester(
    private val scope: CoroutineScope,
    private val eventStream: DomainEventStream,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scraper: (String) -> String = ::runGeminiScraper
) {
    fun start() {
        scope.launch {
            eventStream.events
                .filterIsInstance<StoryDiscovered>()
                .collect { event ->
                    harvest(event)
                }
        }
    }

    private suspend fun harvest(event: StoryDiscovered) {
        logger.debug { "Harvester: Harvesting clean text for story: ${event.title} (${event.url})" }
        
        withContext(ioDispatcher) {
            try {
                // Basic URL validation to mitigate shell injection risks
                validateUrl(event.url)
                
                val cleanText = scraper(event.url)
                if (cleanText.isNotBlank()) {
                    logger.info { "Harvester: Successfully harvested ${cleanText.length} characters for: ${event.title}" }
                    eventStream.emit(
                        HarvestComplete(
                            storyId = event.id,
                            cleanText = cleanText
                        )
                    )
                } else {
                    logger.warn { "Harvester: Scraper returned empty text for: ${event.title}" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Harvester: Error harvesting ${event.url}" }
            }
        }
    }

    private fun validateUrl(urlString: String) {
        // Use URI to validate URL and mitigate shell injection risks
        val url = URI.create(urlString).toURL()
        if (url.protocol != "http" && url.protocol != "https") {
            throw IllegalArgumentException("Invalid protocol: ${url.protocol}")
        }
    }
}

private fun runGeminiScraper(url: String): String {
    val process = ProcessBuilder("gemini", "-p", "Summarize the main content of this webpage in plain text: $url")
        .redirectErrorStream(true)
        .start()

    return try {
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            logger.error { "Harvester: Gemini scraper timed out for URL: $url" }
            process.destroyForcibly()
            return ""
        }
        process.inputStream.bufferedReader().use { it.readText() }.trim()
    } catch (e: Exception) {
        logger.error(e) { "Harvester: Exception during Gemini scraper execution" }
        process.destroyForcibly()
        ""
    } finally {
        process.inputStream.close()
        process.errorStream.close()
        process.outputStream.close()
    }
}

package ai.slopshield.harvester

import ai.slopshield.core.AIService
import ai.slopshield.core.DomainEventStream
import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.StoryDiscovered
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import java.net.URI

private val logger = KotlinLogging.logger {}

private const val scraperPrompt =
    "Please extract the main content of the input stream and output it as a Markdown text. " +
            "Keep the text original, just turn the formatting into Markdown."

/**
 * The Harvester domain service.
 * Fetches webpage content and pipes it into AIService for extraction.
 */
class Harvester(
    private val scope: CoroutineScope,
    private val httpClient: HttpClient,
    private val eventStream: DomainEventStream,
    private val aiService: AIService
) {
    fun start() {
        scope.launch {
            eventStream.events
                .filterIsInstance<StoryDiscovered>()
                .collect { event ->
                    // Launch each harvest in its own coroutine. 
                    // Throttling is handled by the aiService thread pool.
                    launch { harvest(event) }
                }
        }
    }

    private suspend fun harvest(event: StoryDiscovered) {
        logger.info { "Harvester: Fetching and scraping story: ${event.title} (${event.url})" }
        
        try {
            validateUrl(event.url)
            
            // Step 1: Fetch content using Ktor
            val response = httpClient.get(event.url)
            if (!response.status.isSuccess()) {
                logger.warn { "Harvester: Failed to fetch content for ${event.url}. Status: ${response.status}" }
                return
            }
            
            val rawContent = response.bodyAsText()
            
            // Step 2: Scrape using AI Service
            val result = aiService.process(scraperPrompt, rawContent)
            
            logger.info { "Harvester: AI process finished for ${event.title} with exit code: ${result.exitCode}" }
            
            eventStream.emit(
                HarvestComplete(
                    storyId = event.id,
                    cleanText = result.stdout,
                    errorText = result.stderr,
                    exitCode = result.exitCode
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Harvester: Error harvesting ${event.url}" }
        }
    }

    private fun validateUrl(urlString: String) {
        val url = URI.create(urlString).toURL()
        if (url.protocol != "http" && url.protocol != "https") {
            throw IllegalArgumentException("Invalid protocol: ${url.protocol}")
        }
    }
}

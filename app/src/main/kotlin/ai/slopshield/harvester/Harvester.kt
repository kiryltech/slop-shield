package ai.slopshield.harvester

import ai.slopshield.core.AIService
import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.SlopEvent
import ai.slopshield.core.SlopHandler
import ai.slopshield.core.SlopListener
import ai.slopshield.core.StoryDiscovered
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.FlowCollector
import java.net.URI

private val logger = KotlinLogging.logger {}

private const val scraperPrompt =
    "Please extract the main content of the input stream and output it as a Markdown text. " +
            "Keep the text original, just turn the formatting into Markdown."

/**
 * The Harvester domain service.
 * Fetches webpage content and pipes it into AIService for extraction and cleanup.
 * It listens for [StoryDiscovered] events and emits [HarvestComplete] events.
 *
 * @property httpClient The Ktor HTTP client used to fetch content.
 * @property collector The flow collector for emitting new domain events.
 * @property aiService The AI service used to clean up scraped HTML into Markdown.
 */
@SlopListener
class Harvester(
    private val httpClient: HttpClient,
    private val collector: FlowCollector<SlopEvent>,
    private val aiService: AIService
) : SlopHandler<StoryDiscovered> {

    /**
     * Handles the [StoryDiscovered] event by initiating the harvest process.
     *
     * @param event The event containing the story's URL and metadata.
     */
    override suspend fun onEvent(event: StoryDiscovered) {
        harvest(event)
    }

    /**
     * Fetches the content from the provided URL and uses AI to clean it up.
     *
     * @param event The [StoryDiscovered] event providing context for harvesting.
     */
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
            
            collector.emit(
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

    /**
     * Validates that a given string is a valid HTTP or HTTPS URL.
     *
     * @param urlString The string representation of the URL.
     * @throws IllegalArgumentException if the URL protocol is not valid.
     */
    private fun validateUrl(urlString: String) {
        val url = URI.create(urlString).toURL()
        if (url.protocol != "http" && url.protocol != "https") {
            throw IllegalArgumentException("Invalid protocol: ${url.protocol}")
        }
    }
}
package ai.slopshield.harvester

import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.SlopEvent
import ai.slopshield.core.SlopHandler
import ai.slopshield.core.SlopListener
import ai.slopshield.core.StoryDiscovered
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.FlowCollector
import java.net.URI

private val logger = KotlinLogging.logger {}

/**
 * The Harvester domain service.
 * Fetches webpage content and converts it to Markdown.
 * It listens for [StoryDiscovered] events and emits [HarvestComplete] events.
 *
 * @property httpClient The Ktor HTTP client used to fetch content.
 * @property collector The flow collector for emitting new domain events.
 */
@SlopListener
class Harvester(
    private val httpClient: HttpClient,
    private val collector: FlowCollector<SlopEvent>
) : SlopHandler<StoryDiscovered> {

    private val htmlConverter = FlexmarkHtmlConverter.builder().build()

    /**
     * Handles the [StoryDiscovered] event by initiating the harvest process.
     *
     * @param event The event containing the story's URL and metadata.
     */
    override suspend fun onEvent(event: StoryDiscovered) {
        harvest(event)
    }

    /**
     * Fetches the content from the provided URL and converts it to Markdown.
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
                collector.emit(
                    HarvestComplete(
                        storyId = event.id,
                        cleanText = "",
                        success = false
                    )
                )
                return
            }
            
            val rawContent = response.bodyAsText()
            
            // Step 2: Convert HTML to Markdown using Flexmark
            val markdown = htmlConverter.convert(rawContent)
            
            logger.info { "Harvester: Conversion finished for ${event.title}" }
            
            collector.emit(
                HarvestComplete(
                    storyId = event.id,
                    cleanText = markdown,
                    success = true
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Harvester: Error harvesting ${event.url}" }
            collector.emit(
                HarvestComplete(
                    storyId = event.id,
                    cleanText = "",
                    success = false
                )
            )
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

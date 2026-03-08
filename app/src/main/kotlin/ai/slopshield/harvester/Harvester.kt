package ai.slopshield.harvester

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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URI
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Result of the Gemini scraping process.
 */
data class ScraperResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)

/**
 * The Harvester domain service.
 * Fetches webpage content and pipes it into the Gemini CLI for extraction.
 */
class Harvester(
    private val scope: CoroutineScope,
    private val httpClient: HttpClient,
    private val eventStream: DomainEventStream,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxParallelHarvests: Int = 3,
    private val scraper: (String) -> ScraperResult = ::runGeminiScraper
) {
    private val semaphore = Semaphore(maxParallelHarvests)

    fun start() {
        scope.launch {
            eventStream.events
                .filterIsInstance<StoryDiscovered>()
                .collect { event ->
                    launch {
                        semaphore.withPermit {
                            harvest(event)
                        }
                    }
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

            // Step 2: Scrape using Gemini
            withContext(ioDispatcher) {
                val result = scraper(rawContent)

                logger.info { "Harvester: Gemini process finished for ${event.title} with exit code: ${result.exitCode}" }

                eventStream.emit(
                    HarvestComplete(
                        storyId = event.id,
                        cleanText = result.stdout,
                        errorText = result.stderr,
                        exitCode = result.exitCode
                    )
                )
            }
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

private const val scraperPrompt =
    "Please extract the main content of the input stream and output it as a Markdown text. " +
            "Keep the text original, just turn the formatting into Markdown."

private fun runGeminiScraper(content: String): ScraperResult {
    val process = ProcessBuilder("gemini", scraperPrompt, "-e", "")
        .redirectErrorStream(false)
        .start()

    // Write content to process stdin
    process.outputStream.bufferedWriter().use { it.write(content) }

    return try {
        val finished = process.waitFor(120, TimeUnit.SECONDS)

        val stdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()

        if (!finished) {
            logger.error { "Harvester: Gemini scraper timed out" }
            process.destroyForcibly()
            ScraperResult(stdout, stderr, -1)
        } else {
            ScraperResult(stdout, stderr, process.exitValue())
        }
    } catch (e: Exception) {
        logger.error(e) { "Harvester: Exception during Gemini scraper execution" }
        process.destroyForcibly()
        ScraperResult("", e.message ?: "Unknown error", -1)
    } finally {
        process.inputStream.close()
        process.errorStream.close()
        process.outputStream.close()
    }
}

package ai.slopshield.scout

import ai.slopshield.core.SlopEvent
import ai.slopshield.core.SlopService
import ai.slopshield.core.SlopServiceLifecycle
import ai.slopshield.core.StoryDiscovered
import ai.slopshield.core.StoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Data model for a story received from Hacker News API.
 *
 * @property id The Hacker News item ID.
 * @property title The title of the story.
 * @property url The external URL to the story content.
 * @property type The type of item (e.g., "story").
 */
@Serializable
data class HnStory(
    val id: Long,
    val title: String,
    val url: String? = null,
    val type: String? = null
)

/**
 * The Scout domain service.
 * Periodically polls the HN Firebase API for top stories.
 *
 * @property scope The coroutine scope used for polling loops.
 * @property client The Ktor HTTP client to use for network calls.
 * @property collector The flow collector used to emit discovery events.
 * @property repository The underlying repository to check if a story has already been discovered.
 * @property pollInterval The duration to wait between polling loops.
 * @property limit The maximum number of top stories to examine per poll.
 */
@SlopService
class Scout(
    private val scope: CoroutineScope,
    private val client: HttpClient,
    private val collector: FlowCollector<SlopEvent>,
    private val repository: StoryRepository,
    private val pollInterval: Duration = Duration.parse(
        System.getProperty("slopshield.ai.scout.interval", "PT3M")),
    private val limit: Int = Integer.getInteger("slopshield.ai.scout.limit", 30)
) : SlopServiceLifecycle {

    /**
     * Starts the continuous polling loop for top stories.
     */
    override fun start() {
        logger.info { "Scout starting (limit = $limit, poll interval = $pollInterval)..." }
        scope.launch {
            while (isActive) {
                try {
                    pollTopStories()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Scout: Error polling top stories" }
                }
                if (isActive) {
                    delay(pollInterval.toMillis())
                }
            }
        }
    }

    /**
     * Executes a single pass to fetch top stories from HN and emits [StoryDiscovered] events.
     */
    internal suspend fun pollTopStories() {
        logger.debug { "Scout: Polling Hacker News for top stories..." }
        val topIds: List<Long> = client.get("https://hacker-news.firebaseio.com/v0/topstories.json").body()

        topIds.take(limit).forEach { id ->
            val storyId = id.toString()

            // Check if story already exists in DB to avoid redundant emission
            if (repository.get(storyId) != null) {
                return@forEach
            }
            fetchStory(id)?.let { story ->
                val storyUrl = story.url
                if (story.type != "story" || storyUrl == null) {
                    return@let
                }
                logger.info { "Scout: Discovered new story: ${story.title}" }
                collector.emit(
                    StoryDiscovered(
                        id = storyId,
                        title = story.title,
                        url = storyUrl
                    )
                )
            }
        }
    }

    /**
     * Fetches detailed information for a specific HN story ID.
     *
     * @param id The Hacker News story ID to fetch.
     * @return An [HnStory] object if successful, or null on error.
     */
    private suspend fun fetchStory(id: Long): HnStory? {
        return try {
            client.get("https://hacker-news.firebaseio.com/v0/item/$id.json").body()
        } catch (e: Exception) {
            logger.warn(e) { "Scout: Failed to fetch story details for ID: $id" }
            null
        }
    }
}

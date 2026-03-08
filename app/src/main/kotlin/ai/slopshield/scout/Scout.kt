package ai.slopshield.scout

import ai.slopshield.core.SlopEvent
import ai.slopshield.core.SlopService
import ai.slopshield.core.SlopServiceLifecycle
import ai.slopshield.core.StoryDiscovered
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
import java.util.*

private val logger = KotlinLogging.logger {}

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
 */
@SlopService
class Scout(
    private val scope: CoroutineScope,
    private val client: HttpClient,
    private val collector: FlowCollector<SlopEvent>,
    private val pollInterval: Duration = Duration.ofMinutes(15),
    private val limit: Int = Integer.getInteger("slopshield.ai.scout.limit", 30)
) : SlopServiceLifecycle {
    private val discoveredIds = Collections.synchronizedSet(object : LinkedHashSet<Long>() {
        private val MAX_ENTRIES = 1000
        override fun add(element: Long): Boolean {
            if (size >= MAX_ENTRIES) {
                val iterator = iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            return super.add(element)
        }
    })

    override fun start() {
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

    internal suspend fun pollTopStories() {
        logger.debug { "Scout: Polling Hacker News for top stories..." }
        val topIds: List<Long> = client.get("https://hacker-news.firebaseio.com/v0/topstories.json").body()
        
        topIds.take(limit).forEach { id ->
            // Use add() which returns false if the element was already present (atomic for the set)
            if (discoveredIds.add(id)) {
                fetchStory(id)?.let { story ->
                    val storyUrl = story.url
                    if (story.type == "story" && storyUrl != null) {
                        logger.info { "Scout: Discovered new story: ${story.title}" }
                        collector.emit(
                            StoryDiscovered(
                                id = story.id.toString(),
                                title = story.title,
                                url = storyUrl
                            )
                        )
                    } else {
                        // Remove if not a valid story so it can be re-polled if needed
                        discoveredIds.remove(id)
                    }
                } ?: discoveredIds.remove(id) // Remove if fetch failed
            }
        }
    }

    private suspend fun fetchStory(id: Long): HnStory? {
        return try {
            client.get("https://hacker-news.firebaseio.com/v0/item/$id.json").body()
        } catch (e: Exception) {
            logger.warn(e) { "Scout: Failed to fetch story details for ID: $id" }
            null
        }
    }
}

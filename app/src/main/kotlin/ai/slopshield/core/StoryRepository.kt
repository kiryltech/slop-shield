package ai.slopshield.core

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import org.mapdb.DB
import org.mapdb.Serializer
import org.mapdb.serializer.GroupSerializer
import java.util.concurrent.ConcurrentMap
import java.io.Serializable as JavaSerializable

private val logger = KotlinLogging.logger {}

/**
 * Data model for a processed story.
 * Represents an aggregated view of all information extracted for a specific URL.
 *
 * @property id A unique identifier for the story.
 * @property title The original title of the story.
 * @property url The original URL of the story.
 * @property cleanText Extracted clean text content, stripped of formatting.
 * @property category The determined category/type of content.
 * @property categoryReasoning The rationale behind the assigned category.
 * @property analysis The final detailed analysis if the story is deemed high signal.
 */
@Serializable
data class Story(
    val id: String,
    val title: String,
    val url: String,
    val cleanText: String? = null,
    val category: StoryCategory? = null,
    val categoryReasoning: String? = null,
    val analysis: AnalysisComplete? = null
) : JavaSerializable


/**
 * Repository for managing persistent storage of stories using MapDB.
 * Provides thread-safe CRUD operations.
 *
 * @property db The underlying MapDB instance used for storage.
 */
class StoryRepository(
    private val db: DB
) {

    // Persistent Map for stories
    private val stories: ConcurrentMap<String, Story> = db
        .treeMap("stories")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.JAVA as GroupSerializer<Story>)
        .createOrOpen()

    /**
     * Retrieves a story by its ID.
     *
     * @param id The unique identifier of the story.
     * @return The found [Story] or null if it does not exist.
     */
    fun get(id: String): Story? = stories[id]

    /**
     * Persists or updates a story.
     *
     * @param story The [Story] to be saved.
     */
    fun upsert(story: Story) {
        try {
            stories[story.id] = story
            db.commit()
            logger.debug { "StoryRepository: Upserted story ${story.id}" }
        } catch (e: Exception) {
            logger.error(e) { "StoryRepository: Failed to upsert story ${story.id}" }
        }
    }

    /**
     * Updates an existing story using a transformation function.
     * Useful for surgical updates (e.g., just adding cleanText).
     *
     * @param id The ID of the story to update.
     * @param transform A function that applies the changes to the existing story.
     */
    fun update(id: String, transform: (Story) -> Story) {
        try {
            val existing = stories[id]
            if (existing != null) {
                stories[id] = transform(existing)
                db.commit()
                logger.debug { "StoryRepository: Updated story $id" }
            } else {
                logger.warn { "StoryRepository: Attempted to update non-existent story $id" }
            }
        } catch (e: Exception) {
            logger.error(e) { "StoryRepository: Failed to update story $id" }
        }
    }

    /**
     * Returns all stories as a Sequence for scalability.
     *
     * @return A sequence of all stored [Story] instances.
     */
    fun getAll(): Sequence<Story> = stories.values.asSequence()

    /**
     * Closes the underlying database, releasing resources.
     */
    fun close() {
        db.close()
    }
}
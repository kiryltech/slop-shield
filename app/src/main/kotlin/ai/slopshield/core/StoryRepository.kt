package ai.slopshield.core

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import org.mapdb.DB
import org.mapdb.Serializer
import org.mapdb.serializer.GroupSerializer
import java.io.Serializable as JavaSerializable
import java.util.concurrent.ConcurrentMap

private val logger = KotlinLogging.logger {}

/**
 * Data model for a processed story.
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
     */
    fun get(id: String): Story? = stories[id]

    /**
     * Persists or updates a story.
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
     */
    fun getAll(): Sequence<Story> = stories.values.asSequence()

    fun close() {
        db.close()
    }
}

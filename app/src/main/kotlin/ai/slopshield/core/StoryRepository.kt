package ai.slopshield.core

import io.github.oshai.kotlinlogging.KotlinLogging
import org.mapdb.DB
import org.mapdb.DBMaker
import org.mapdb.Serializer
import java.io.Serializable
import java.util.concurrent.ConcurrentMap

private val logger = KotlinLogging.logger {}

/**
 * Domain model representing a processed story.
 */
data class Story(
    val id: String,
    val title: String,
    val url: String,
    val cleanText: String? = null,
    val category: StoryCategory? = null,
    val analysis: AnalysisComplete? = null
) : Serializable

/**
 * Repository for managing persistent storage of stories using MapDB.
 */
class StoryRepository(
    dbFilePath: String = System.getProperty("slopshield.db.path", "slopshield.db")
) {
    private val db: DB = DBMaker
        .fileDB(dbFilePath)
        .transactionEnable()
        .closeOnJvmShutdown()
        .make()

    // Persistent Map for stories
    private val stories: ConcurrentMap<String, Story> = db
        .hashMap("stories")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.JAVA as Serializer<Story>)
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
            db.rollback()
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
            db.rollback()
            logger.error(e) { "StoryRepository: Failed to update story $id" }
        }
    }

    /**
     * Returns all stories.
     */
    fun getAll(): List<Story> = stories.values.toList()

    fun close() {
        db.close()
    }
}

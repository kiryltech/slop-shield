package ai.slopshield.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.mapdb.DBMaker
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for the [StoryProjector] to ensure domain events are correctly projected into the read model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryProjectorTest {
    private lateinit var repository: StoryRepository
    private lateinit var projector: StoryProjector

    /**
     * Sets up the in-memory repository and projector before each test.
     */
    @BeforeTest
    fun setup() {
        val db = DBMaker.memoryDB().transactionEnable().make()
        repository = StoryRepository(db)
        projector = StoryProjector(repository)
    }

    /**
     * Cleans up the in-memory database after each test.
     */
    @AfterTest
    fun tearDown() {
        repository.close()
    }

    /**
     * Verifies that a [StoryDiscovered] event successfully creates a new story entry.
     */
    @Test
    fun `test StoryDiscovered projects new story`() = runTest {
        val event = StoryDiscovered("1", "Title", "URL")
        projector.onEvent(event)

        val story = repository.get("1")
        assertNotNull(story)
        assertEquals("Title", story.title)
    }

    /**
     * Verifies that a [HarvestComplete] event updates an existing story with clean text.
     */
    @Test
    fun `test HarvestComplete projects clean text`() = runTest {
        repository.upsert(Story("1", "Title", "URL"))
        val event = HarvestComplete("1", "Clean Text", true)
        projector.onEvent(event)

        val story = repository.get("1")
        assertNotNull(story)
        assertEquals("Clean Text", story.cleanText)
    }

    /**
     * Verifies that a failed [HarvestComplete] event marks the story as failed.
     */
    @Test
    fun `test HarvestComplete projects failure`() = runTest {
        repository.upsert(Story("1", "Title", "URL"))
        val event = HarvestComplete("1", "", false)
        projector.onEvent(event)

        val story = repository.get("1")
        assertNotNull(story)
        assertEquals(true, story.failed)
    }

    /**
     * Verifies that a [StoryCategorized] event updates an existing story with category and reasoning.
     */
    @Test
    fun `test StoryCategorized projects category`() = runTest {
        repository.upsert(Story("1", "Title", "URL"))
        val event = StoryCategorized("1", StoryCategory.WRITING, "Reasoning")
        projector.onEvent(event)

        val story = repository.get("1")
        assertNotNull(story)
        assertEquals(StoryCategory.WRITING, story.category)
        assertEquals("Reasoning", story.categoryReasoning)
    }
}

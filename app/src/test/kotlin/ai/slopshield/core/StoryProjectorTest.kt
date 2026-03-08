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

@OptIn(ExperimentalCoroutinesApi::class)
class StoryProjectorTest {
    private lateinit var repository: StoryRepository
    private lateinit var projector: StoryProjector

    @BeforeTest
    fun setup() {
        val db = DBMaker.memoryDB().transactionEnable().make()
        repository = StoryRepository(db)
        projector = StoryProjector(repository)
    }

    @AfterTest
    fun tearDown() {
        repository.close()
    }

    @Test
    fun `test StoryDiscovered projects new story`() = runTest {
        val event = StoryDiscovered("1", "Title", "URL")
        projector.onEvent(event)

        val story = repository.get("1")
        assertNotNull(story)
        assertEquals("Title", story.title)
    }

    @Test
    fun `test HarvestComplete projects clean text`() = runTest {
        repository.upsert(Story("1", "Title", "URL"))
        val event = HarvestComplete("1", "Clean Text", "", 0)
        projector.onEvent(event)

        val story = repository.get("1")
        assertNotNull(story)
        assertEquals("Clean Text", story.cleanText)
    }

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

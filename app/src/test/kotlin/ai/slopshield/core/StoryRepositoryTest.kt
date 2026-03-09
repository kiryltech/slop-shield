package ai.slopshield.core

import org.mapdb.DBMaker
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for the [StoryRepository] verifying CRUD operations against the MapDB backend.
 */
class StoryRepositoryTest {
    private lateinit var repository: StoryRepository

    /**
     * Initializes an in-memory MapDB instance before each test.
     */
    @BeforeTest
    fun setup() {
        val db = DBMaker.memoryDB().transactionEnable().make()
        repository = StoryRepository(db)
    }

    /**
     * Closes the MapDB instance after each test.
     */
    @AfterTest
    fun tearDown() {
        repository.close()
    }

    /**
     * Verifies basic upsert and retrieval functionality.
     */
    @Test
    fun `test upsert and get`() {
        val story = Story(id = "1", title = "Test Story", url = "http://example.com")
        repository.upsert(story)

        val retrieved = repository.get("1")
        assertNotNull(retrieved)
        assertEquals(story.title, retrieved.title)
    }

    /**
     * Verifies the transformation update logic.
     */
    @Test
    fun `test update with transform`() {
        val story = Story(id = "1", title = "Test Story", url = "http://example.com")
        repository.upsert(story)

        repository.update("1") { it.copy(cleanText = "Transformed text") }

        val retrieved = repository.get("1")
        assertNotNull(retrieved)
        assertEquals("Transformed text", retrieved.cleanText)
    }

    /**
     * Verifies that updating a non-existent story is safely handled without throwing exceptions.
     */
    @Test
    fun `test update non-existent story`() {
        repository.update("999") { it.copy(cleanText = "Fail") }
        assertNull(repository.get("999"))
    }

    /**
     * Verifies that retrieving all stories works and returns them as a sequence.
     */
    @Test
    fun `test getAll as sequence`() {
        repository.upsert(Story(id = "1", title = "S1", url = "url1"))
        repository.upsert(Story(id = "2", title = "S2", url = "url2"))

        val all = repository.getAll().toList()
        assertEquals(2, all.size)
    }
}

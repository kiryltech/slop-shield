package ai.slopshield.harvester

import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.InternalDomainEventStream
import ai.slopshield.core.StoryDiscovered
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HarvesterTest {

    @BeforeTest
    fun setup() {
        InternalDomainEventStream.reset()
    }

    @Test
    fun `test harvester reacts to StoryDiscovered and emits HarvestComplete`() = runTest {
        val storyId = "123"
        val storyTitle = "Test Story"
        val storyUrl = "http://example.com"
        val expectedText = "Cleaned content from Gemini"

        val harvester = Harvester(
            scope = backgroundScope,
            eventStream = InternalDomainEventStream,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            scraper = { url -> if (url == storyUrl) expectedText else "" }
        )
        harvester.start()

        // Emit discovery event
        InternalDomainEventStream.emit(
            StoryDiscovered(id = storyId, title = storyTitle, url = storyUrl)
        )

        val harvestEvents = InternalDomainEventStream.events
            .filterIsInstance<HarvestComplete>()
            .take(1)
            .toList()

        assertEquals(1, harvestEvents.size)
        assertEquals(storyId, harvestEvents[0].storyId)
        assertEquals(expectedText, harvestEvents[0].cleanText)
    }
}

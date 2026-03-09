package ai.slopshield.core

import ai.slopshield.observability.RecentActivityRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.mapdb.DBMaker
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Functional test for EventCoordinator using real reflection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventCoordinatorTest {

    @SlopListener
    class GlobalTestHandler : SlopHandler<StoryDiscovered> {
        companion object {
            var lastEvent: StoryDiscovered? = null
        }
        override suspend fun onEvent(event: StoryDiscovered) {
            lastEvent = event
        }
    }

    @SlopService
    class GlobalTestService : SlopServiceLifecycle {
        companion object {
            var started = false
            var stopped = false
        }
        override fun start() { started = true }
        override fun stop() { stopped = true }
    }

    @BeforeTest
    fun setup() {
        GlobalTestHandler.lastEvent = null
        GlobalTestService.started = false
        GlobalTestService.stopped = false
    }

    @Test
    fun `test component discovery and lifecycle`() = runTest {
        val eventStream = MutableSharedFlow<SlopEvent>(replay = 64)
        val activityStream = MutableSharedFlow<ActivityEvent>(replay = 64)
        val recentActivity = RecentActivityRegistry(this, activityStream)
        val registry = mapOf(
            CoroutineScope::class to this,
            MutableSharedFlow::class to eventStream,
            StoryRepository::class to StoryRepository(DBMaker.memoryDB().make()),
            AIService::class to AIService(),
            RecentActivityRegistry::class to recentActivity
        )
        
        val coordinator = EventCoordinator(this, eventStream, activityStream, registry)
        coordinator.start("ai.slopshield.core") // Scan this package

        // Verify service started
        assertTrue(GlobalTestService.started, "Service should have been started")

        // Dispatch event
        val event = StoryDiscovered("1", "Title", "URL")
        eventStream.emit(event)
        
        // Wait for asynchronous dispatch
        withTimeout(2000) {
            while (GlobalTestHandler.lastEvent == null) {
                delay(10)
            }
        }

        assertEquals(event, GlobalTestHandler.lastEvent)

        coordinator.stop()
        assertTrue(GlobalTestService.stopped, "Service should have been stopped")
    }

    @Test
    fun `test reflection instantiation with dependencies`() {
        // Internal test for the instantiate method logic
        val eventStream = MutableSharedFlow<SlopEvent>()
        val activityStream = MutableSharedFlow<ActivityEvent>()
        val recentActivity = RecentActivityRegistry(CoroutineScope(Job()), activityStream)
        val registry = mapOf(
            MutableSharedFlow::class to eventStream,
            CoroutineScope::class to CoroutineScope(Dispatchers.Default),
            RecentActivityRegistry::class to recentActivity
        )
        val coordinator = EventCoordinator(CoroutineScope(Job()), eventStream, activityStream, registry)
        
        // Use reflection to access private instantiate for testing or just rely on start() test above
    }
}

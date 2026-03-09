package ai.slopshield.observability

import ai.slopshield.core.ActivityEvent
import ai.slopshield.core.HandlerFinished
import ai.slopshield.core.HandlerStarted
import ai.slopshield.core.SlopService
import ai.slopshield.core.SlopServiceLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Keeps track of recent activity events to provide initial state to the UI on reload.
 */
@SlopService
class RecentActivityRegistry(
    private val scope: CoroutineScope,
    private val activityStream: MutableSharedFlow<ActivityEvent>
) : SlopServiceLifecycle {

    private val recentEvents = ConcurrentLinkedDeque<ActivityEvent>()
    private val maxEvents = 20

    override fun start() {
        scope.launch {
            activityStream.collect { event ->
                recentEvents.addFirst(event)
                while (recentEvents.size > maxEvents) {
                    recentEvents.removeLast()
                }
            }
        }
    }

    fun getRecentEvents(): List<ActivityEvent> = recentEvents.toList()

    /**
     * Calculates the current number of active workers based on the history.
     * Note: This is an approximation based on the visible history.
     */
    fun getActiveWorkersCount(): Int {
        val started = recentEvents.count { it is HandlerStarted }
        val finished = recentEvents.count { it is HandlerFinished }
        return Math.max(0, started - finished)
    }
}

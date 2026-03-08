package ai.slopshield.core

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * The StoryProjector listens to all domain events and updates the StoryRepository.
 * It uses the [ProjectableEvent] interface to delegate the specific update logic
 * to the events themselves.
 */
class StoryProjector(
    private val scope: CoroutineScope,
    private val eventStream: DomainEventStream,
    private val repository: StoryRepository
) {
    fun start() {
        logger.info { "StoryProjector: Starting state projection..." }
        
        scope.launch {
            eventStream.events
                .filterIsInstance<ProjectableEvent>()
                .collect { event ->
                    logger.debug { "StoryProjector: Projecting event ${event::class.simpleName}" }
                    event.project(repository)
                }
        }
    }
}

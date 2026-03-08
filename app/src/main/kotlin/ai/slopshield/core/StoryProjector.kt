package ai.slopshield.core

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * The StoryProjector listens to all projectable events and updates the StoryRepository.
 */
@SlopListener
class StoryProjector(
    private val repository: StoryRepository
) : SlopHandler<ProjectableEvent> {

    override suspend fun onEvent(event: ProjectableEvent) =
        event.project(repository)
}

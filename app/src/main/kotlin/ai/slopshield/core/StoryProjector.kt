package ai.slopshield.core

/**
 * The StoryProjector listens to all projectable events and updates the StoryRepository.
 * It serves as a read-model updater, taking newly computed information from events
 * and merging it into the central persistent store.
 *
 * @property repository The underlying storage mechanism for stories.
 */
@SlopListener
class StoryProjector(
    private val repository: StoryRepository
) : SlopHandler<ProjectableEvent> {

    /**
     * Handles incoming projectable events and delegates the projection process to the event itself.
     *
     * @param event The event containing data to be projected into the repository.
     */
    override suspend fun onEvent(event: ProjectableEvent) =
        event.project(repository)
}

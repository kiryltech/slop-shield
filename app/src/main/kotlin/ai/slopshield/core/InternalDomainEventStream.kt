package ai.slopshield.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Interface for the Domain Event Stream to allow for better test isolation and DI.
 */
interface DomainEventStream {
    val events: SharedFlow<SlopEvent>
    suspend fun emit(event: SlopEvent)
}

/**
 * Default implementation of the Domain Event Stream.
 */
class DefaultDomainEventStream(replay: Int = 64) : DomainEventStream {
    private val _events = MutableSharedFlow<SlopEvent>(replay = replay)
    override val events: SharedFlow<SlopEvent> = _events.asSharedFlow()

    override suspend fun emit(event: SlopEvent) {
        _events.emit(event)
    }
}

/**
 * Singleton instance for the Modular Monolith core.
 */
object InternalDomainEventStream : DomainEventStream {
    private var delegate = DefaultDomainEventStream()
    
    override val events: SharedFlow<SlopEvent> get() = delegate.events

    override suspend fun emit(event: SlopEvent) {
        delegate.emit(event)
    }

    internal fun reset() {
        delegate = DefaultDomainEventStream()
    }
}

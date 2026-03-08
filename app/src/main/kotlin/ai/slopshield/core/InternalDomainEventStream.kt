package ai.slopshield.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The Domain Event Stream for Project SlopShield.
 * This is the central hub for the Modular Monolith, replacing the "Kafka Cult"
 * with an efficient, in-process event system.
 */
object InternalDomainEventStream {
    private val _events = MutableSharedFlow<SlopEvent>()
    val events: SharedFlow<SlopEvent> = _events.asSharedFlow()

    suspend fun emit(event: SlopEvent) {
        _events.emit(event)
    }
}

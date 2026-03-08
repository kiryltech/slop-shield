package ai.slopshield.core

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.reflections.Reflections
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

private val logger = KotlinLogging.logger {}

/**
 * Discovers and orchestrates all SlopHandlers.
 * It uses reflection to find annotated listeners and manages their lifecycle.
 */
class EventCoordinator(
    private val scope: CoroutineScope,
    private val eventStream: DomainEventStream,
    private val registry: Map<KClass<*>, Any>
) {
    private val handlers = mutableListOf<SlopHandler<*>>()

    fun start(packageName: String = "ai.slopshield") {
        logger.info { "EventCoordinator: Scanning for listeners in $packageName..." }
        
        val reflections = Reflections(packageName)
        val listenerClasses = reflections.getTypesAnnotatedWith(SlopListener::class.java)

        listenerClasses.forEach { clazz ->
            if (SlopHandler::class.java.isAssignableFrom(clazz)) {
                try {
                    val handler = instantiateHandler(clazz.kotlin)
                    handlers.add(handler)
                    logger.info { "EventCoordinator: Registered handler ${clazz.simpleName} for event ${handler.eventType.simpleName}" }
                } catch (e: Exception) {
                    logger.error(e) { "EventCoordinator: Failed to instantiate listener ${clazz.name}" }
                }
            }
        }

        // Start listening
        scope.launch {
            eventStream.events.collect { event ->
                dispatch(event)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun dispatch(event: SlopEvent) {
        handlers.filter { it.eventType.isInstance(event) }
            .forEach { handler ->
                val typedHandler = handler as SlopHandler<SlopEvent>
                if (typedHandler.canHandle(event)) {
                    scope.launch {
                        try {
                            typedHandler.onEvent(event)
                        } catch (e: Exception) {
                            logger.error(e) { "EventCoordinator: Handler ${handler::class.simpleName} failed for event ${event::class.simpleName}" }
                        }
                    }
                }
            }
    }

    private fun instantiateHandler(clazz: KClass<*>): SlopHandler<*> {
        val constructor = clazz.primaryConstructor ?: return clazz.createInstance() as SlopHandler<*>
        
        val args = constructor.parameters.map { param ->
            val paramType = param.type.classifier as? KClass<*>
            // Find a registry entry where the key is a subclass of the required type
            val dependency = registry.entries.find { (type, _) -> 
                paramType != null && type.isSubclassOf(paramType)
            }?.value
            
            dependency ?: throw IllegalArgumentException("No dependency found for type $paramType in constructor of $clazz")
        }

        return constructor.call(*args.toTypedArray()) as SlopHandler<*>
    }
}

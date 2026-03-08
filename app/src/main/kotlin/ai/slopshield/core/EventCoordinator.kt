package ai.slopshield.core

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.reflections.Reflections
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

private val logger = KotlinLogging.logger {}

/**
 * Discovers and orchestrates all SlopHandlers and SlopServices.
 * It uses reflection to find annotated components and manages their lifecycle.
 */
class EventCoordinator(
    private val scope: CoroutineScope,
    private val eventStream: SharedFlow<SlopEvent>,
    private val registry: Map<KClass<*>, Any>
) {
    private val handlers = mutableListOf<SlopHandler<*>>()
    private val services = mutableListOf<SlopServiceLifecycle>()

    fun start(packageName: String = "ai.slopshield") {
        logger.info { "EventCoordinator: Scanning for components in $packageName..." }
        
        val reflections = Reflections(packageName)
        
        // 1. Discover Handlers (Listeners)
        reflections.getTypesAnnotatedWith(SlopListener::class.java).forEach { clazz ->
            if (SlopHandler::class.java.isAssignableFrom(clazz)) {
                val handler = instantiate(clazz.kotlin) as SlopHandler<*>
                handlers.add(handler)
                logger.info { "EventCoordinator: Registered handler ${clazz.simpleName} for event ${handler.eventType.simpleName}" }
            }
        }

        // 2. Discover Services (Producers/Servers)
        reflections.getTypesAnnotatedWith(SlopService::class.java).forEach { clazz ->
            if (SlopServiceLifecycle::class.java.isAssignableFrom(clazz)) {
                val service = instantiate(clazz.kotlin) as SlopServiceLifecycle
                services.add(service)
                service.start()
                logger.info { "EventCoordinator: Started service ${clazz.simpleName}" }
            }
        }

        // 3. Start Event Dispatch Loop
        scope.launch {
            eventStream.collect { event ->
                dispatch(event)
            }
        }
    }

    fun stop() {
        logger.info { "EventCoordinator: Stopping all services..." }
        services.forEach { it.stop() }
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

    private fun instantiate(clazz: KClass<*>): Any {
        val constructor = clazz.primaryConstructor ?: return clazz.createInstance()
        
        val args = mutableMapOf<KParameter, Any?>()
        constructor.parameters.forEach { param ->
            val paramType = param.type.classifier as? KClass<*>
            val dependency = registry.entries.find { (type, _) -> 
                paramType != null && type.isSubclassOf(paramType)
            }?.value
            
            if (dependency != null) {
                args[param] = dependency
            } else if (!param.isOptional) {
                throw IllegalArgumentException("No dependency found for required type $paramType in constructor of $clazz")
            }
        }

        return constructor.callBy(args)
    }
}

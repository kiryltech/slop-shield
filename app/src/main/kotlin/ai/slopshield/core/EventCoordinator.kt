package ai.slopshield.core

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.reflections.Reflections
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

private val logger = KotlinLogging.logger {}

/**
 * Discovers and orchestrates all SlopHandlers and SlopServices.
 * It uses reflection to find annotated components and manages their lifecycle.
 *
 * @property scope The coroutine scope used for launching background jobs.
 * @property eventStream The main stream of domain events.
 * @property activityStream The stream of system activity events.
 * @property registry A map holding pre-instantiated dependencies for injection.
 */
class EventCoordinator(
    private val scope: CoroutineScope,
    private val eventStream: MutableSharedFlow<SlopEvent>,
    private val activityStream: MutableSharedFlow<ActivityEvent>,
    private val registry: Map<KClass<*>, Any>
) {
    private val handlers = mutableListOf<SlopHandler<*>>()
    private val services = mutableListOf<SlopServiceLifecycle>()
    private var dispatchJob: kotlinx.coroutines.Job? = null
    private val activeWorkersCounter = AtomicInteger(0)

    /**
     * Scans the specified package for annotated components and starts them.
     *
     * @param packageName The base package name to scan. Defaults to "ai.slopshield".
     */
    fun start(packageName: String = "ai.slopshield") {
        logger.info { "EventCoordinator: Scanning for components in $packageName..." }

        val reflections = Reflections(packageName)
        val componentClasses = (reflections.getTypesAnnotatedWith(SlopListener::class.java) + 
                               reflections.getTypesAnnotatedWith(SlopService::class.java)).distinct()

        componentClasses.forEach { clazz ->
            if (!shouldRegister(clazz)) return@forEach

            val instance = instantiate(clazz.kotlin)

            // Register as Handler if applicable
            if (instance is SlopHandler<*> && clazz.isAnnotationPresent(SlopListener::class.java)) {
                handlers.add(instance)
                logger.info { "EventCoordinator: Registered handler ${clazz.simpleName} for event ${instance.eventType.simpleName}" }
            }

            // Register as Service if applicable
            if (instance is SlopServiceLifecycle && clazz.isAnnotationPresent(SlopService::class.java)) {
                services.add(instance)
                instance.start()
                logger.info { "EventCoordinator: Started service ${clazz.simpleName}" }
            }
        }

        // 3. Start Event Dispatch Loop
        dispatchJob = scope.launch(SupervisorJob()) {
            eventStream.collect { event ->
                dispatch(event)
            }
        }
    }

    /**
     * Stops the event dispatch loop and shuts down all registered services.
     */
    fun stop() {
        logger.info { "EventCoordinator: Stopping all services..." }
        dispatchJob?.cancel()
        services.forEach { it.stop() }
    }

    /**
     * Dispatches an incoming event to all applicable handlers.
     *
     * @param event The event to be dispatched.
     */
    @Suppress("UNCHECKED_CAST")
    private fun dispatch(event: SlopEvent) {
        handlers.filter { it.eventType.isInstance(event) }
            .forEach { handler ->
                val typedHandler = handler as SlopHandler<SlopEvent>
                if (typedHandler.canHandle(event)) {
                    scope.launch {
                        val startTime = Instant.now()
                        val storyId = getStoryId(event)
                        val started = HandlerStarted(
                            handler = handler::class.simpleName!!, 
                            event = event::class.simpleName!!, 
                            storyId = storyId,
                            activeWorkers = activeWorkersCounter.incrementAndGet()
                        )
                        activityStream.emit(started)
                        
                        try {
                            typedHandler.onEvent(event)
                            val elapsed = Duration.between(startTime, Instant.now()).toMillis()
                            activityStream.emit(HandlerFinished(
                                handler = handler::class.simpleName!!, 
                                event = event::class.simpleName!!, 
                                executionId = started.executionId, 
                                storyId = storyId, 
                                elapsedMs = elapsed, 
                                success = true,
                                activeWorkers = activeWorkersCounter.decrementAndGet()
                            ))
                        } catch (e: Exception) {
                            logger.error(e) { "EventCoordinator: Handler ${handler::class.simpleName} failed for event ${event::class.simpleName}" }
                            val elapsed = Duration.between(startTime, Instant.now()).toMillis()
                            
                            // Emit failure activity event
                            activityStream.emit(HandlerFinished(
                                handler = handler::class.simpleName!!, 
                                event = event::class.simpleName!!, 
                                executionId = started.executionId, 
                                storyId = storyId, 
                                elapsedMs = elapsed, 
                                success = false,
                                activeWorkers = activeWorkersCounter.decrementAndGet()
                            ))
                            
                            // Emit domain failure event if story context is available
                            if (storyId != null) {
                                eventStream.emit(ProcessingFailed(
                                    storyId = storyId,
                                    handler = handler::class.simpleName!!,
                                    errorMessage = e.message ?: "Unknown error"
                                ))
                            }
                        }
                    }
                }
            }
    }

    /**
     * Attempts to dynamically extract a story ID from an event using reflection.
     *
     * @param event The event to inspect.
     * @return The story ID as a string, or null if it cannot be found.
     */
    private fun getStoryId(event: SlopEvent): String? {
        // Reflection-based way to get storyId from event if it exists
        return try {
            val property = event::class.members.find { it.name == "storyId" || it.name == "id" }
            property?.call(event)?.toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Evaluates if a component should be registered based on its conditional annotations.
     *
     * @param clazz The class to evaluate.
     * @return True if the component should be registered, false otherwise.
     */
    private fun shouldRegister(clazz: Class<*>): Boolean {
        val enabledAnnotation = clazz.getAnnotation(Enabled::class.java)
            ?: return true

        return try {
            val conditionClass = enabledAnnotation.condition.java
            val condition = conditionClass.getDeclaredConstructor().newInstance()
            val enabled = condition.isEnabled()

            if (!enabled) {
                logger.debug { "EventCoordinator: Skipping ${clazz.simpleName} because condition ${enabledAnnotation.condition.simpleName} was not met." }
            }

            enabled
        } catch (e: Exception) {
            logger.error(e) { "EventCoordinator: Failed to evaluate condition for ${clazz.name}" }
            false
        }
    }

    /**
     * Instantiates a class and resolves its constructor dependencies.
     *
     * @param clazz The Kotlin class to instantiate.
     * @return The instantiated object.
     * @throws IllegalArgumentException if a required dependency is missing.
     */
    private fun instantiate(clazz: KClass<*>): Any {
        // First check if the class or its superclass is in the registry
        val existing = registry.entries.find { (type, _) ->
            clazz.isSubclassOf(type)
        }?.value
        
        if (existing != null) {
            logger.debug { "EventCoordinator: Using existing instance from registry for ${clazz.simpleName}" }
            return existing
        }

        val constructor = clazz.primaryConstructor ?: return clazz.createInstance()

        val args = mutableMapOf<KParameter, Any?>()
        constructor.parameters.forEach { param ->
            val paramType = param.type.classifier as? KClass<*>
            
            // Special handling for the two streams
            val dependency = if (paramType == MutableSharedFlow::class || paramType == SharedFlow::class || paramType == FlowCollector::class) {
                val typeArg = param.type.arguments.firstOrNull()?.type?.classifier as? KClass<*>
                if (typeArg != null && typeArg.isSubclassOf(ActivityEvent::class)) {
                    activityStream
                } else {
                    eventStream
                }
            } else {
                registry.entries.find { (type, _) ->
                    paramType != null && type.isSubclassOf(paramType)
                }?.value
            }

            if (dependency != null) {
                args[param] = dependency
            } else if (!param.isOptional) {
                throw IllegalArgumentException("No dependency found for required type $paramType in constructor of $clazz")
            }
        }

        return constructor.callBy(args)
    }
}
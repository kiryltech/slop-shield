package ai.slopshield.core

import java.time.Instant
import kotlin.reflect.KClass
import java.lang.reflect.ParameterizedType

/**
 * Functional interface for evaluating whether a component should be enabled.
 */
interface EnabledIf {
    fun isEnabled(): Boolean
}

/**
 * Default implementation that checks a system property.
 */
class SystemPropertyCondition(
    private val property: String,
    private val expected: String
) : EnabledIf {
    override fun isEnabled(): Boolean = System.getProperty(property) == expected
}

/**
 * Specific condition for debug mode.
 */
class DebugEnabledCondition : EnabledIf {
    override fun isEnabled()= java.lang.Boolean.getBoolean("slopshield.debug")
}

/**
 * Annotation to mark a class as an event listener.
 */
@Target(AnnotationTarget.CLASS)
annotation class SlopListener

/**
 * Annotation to mark a class as a standalone domain service.
 */
@Target(AnnotationTarget.CLASS)
annotation class SlopService

/**
 * Conditional annotation to control component initialization.
 * 
 * @param condition A class implementing [EnabledIf] that will be used to evaluate
 * whether this component should be registered.
 */
@Target(AnnotationTarget.CLASS)
annotation class Enabled(val condition: KClass<out EnabledIf>)

/**
 * Interface for active domain services that need explicit starting and optional stopping.
 */
interface SlopServiceLifecycle {
    fun start()
    fun stop() {}
}

/**
 * Interface for components that handle domain events.
 * 
 * @param T The specific type of [SlopEvent] this handler processes.
 */
interface SlopHandler<T : SlopEvent> {
    /**
     * The type of event this handler is interested in.
     * Derived at runtime from the generic type parameter and cached.
     */
    @Suppress("UNCHECKED_CAST")
    val eventType: KClass<T>
        get() = CachedTypeRegistry.get(this::class) as KClass<T>

    /**
     * Optional filter to further refine which events are processed.
     */
    fun canHandle(event: T): Boolean = true

    /**
     * Processes the event.
     */
    suspend fun onEvent(event: T)
}

/**
 * Base interface for all events within the SlopShield Domain Event Stream.
 */
sealed interface SlopEvent {
    /**
     * The point in time when the event was created.
     */
    val timestamp: Instant
}

/**
 * An event that knows how to project itself into the [StoryRepository].
 */
interface ProjectableEvent : SlopEvent {
    /**
     * Projects the event's data into the repository.
     */
    fun project(repository: StoryRepository)
}

/**
 * Triggered by the Scout when a new story is identified from an external source (e.g., Hacker News).
 */
data class StoryDiscovered(
    val id: String,
    val title: String,
    val url: String,
    override val timestamp: Instant = Instant.now()
) : ProjectableEvent {
    override fun project(repository: StoryRepository) {
        repository.upsert(Story(id = id, title = title, url = url))
    }
}

/**
 * Triggered by the Harvester after successfully extracting and cleaning text from a story's URL.
 */
data class HarvestComplete(
    val storyId: String,
    val cleanText: String,
    val errorText: String,
    val exitCode: Int,
    override val timestamp: Instant = Instant.now()
) : ProjectableEvent {
    override fun project(repository: StoryRepository) {
        if (exitCode == 0) {
            repository.update(storyId) { it.copy(cleanText = cleanText) }
        }
    }
}

/**
 * Categories for the identified content type.
 */
enum class StoryCategory {
    WRITING, PRODUCT, DEMO, SOURCE, UNKNOWN
}

/**
 * Triggered by the Strategist after identifying the type of content.
 */
data class StoryCategorized(
    val storyId: String,
    val category: StoryCategory,
    val reasoning: String,
    override val timestamp: Instant = Instant.now()
) : ProjectableEvent {
    override fun project(repository: StoryRepository) {
        repository.update(storyId) { it.copy(category = category, categoryReasoning = reasoning) }
    }
}

/**
 * Triggered by the Memory domain in response to a context request.
 */
data class ContextResponse(
    val content: String,
    override val timestamp: Instant = Instant.now()
) : SlopEvent

enum class Alignment {
    ECHO_CHAMBER, OPPOSITE_VIEW, COMPLEMENTARY
}

enum class HypeRisk {
    LOW, MEDIUM, HIGH
}

/**
 * The final output of the Strategist (The Curator).
 */
data class AnalysisComplete(
    val storyId: String,
    val mms: Int,
    val sa: Int,
    val sd: Int,
    val d: Int,
    val alignment: Alignment,
    val hypeRisk: HypeRisk,
    val sparringNote: String,
    override val timestamp: Instant = Instant.now()
) : ProjectableEvent {
    /**
     * The weighted average score derived from the SECV rubric.
     */
    val totalScore: Double get() = (mms + sa + sd + d) / 4.0

    override fun project(repository: StoryRepository) {
        repository.update(storyId) { it.copy(analysis = this) }
    }
}

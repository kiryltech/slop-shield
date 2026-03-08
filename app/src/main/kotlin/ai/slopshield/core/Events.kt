package ai.slopshield.core

import java.lang.reflect.ParameterizedType
import java.time.Instant
import kotlin.reflect.KClass

/**
 * Annotation to mark a class as an event listener.
 * These will be discovered and instantiated by the [EventCoordinator].
 */
@Target(AnnotationTarget.CLASS)
annotation class SlopListener

/**
 * Annotation to mark a class as a standalone domain service.
 * These will be discovered and started by the [EventCoordinator].
 */
@Target(AnnotationTarget.CLASS)
annotation class SlopService

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
     * Derived at runtime from the generic type parameter.
     */
    @Suppress("UNCHECKED_CAST")
    val eventType: KClass<T>
        get() {
            // Find the SlopHandler interface in the hierarchy
            val type = this::class.java.genericInterfaces
                .filterIsInstance<ParameterizedType>()
                .first { it.rawType == SlopHandler::class.java }
                .actualTypeArguments[0]
            
            return (type as Class<T>).kotlin
        }

    /**
     * Optional filter to further refine which events are processed.
     * 
     * @param event The event to check.
     * @return True if the handler can process this specific event instance.
     */
    fun canHandle(event: T): Boolean = true

    /**
     * Processes the event.
     * 
     * @param event The event instance to process.
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
     * 
     * @param repository The story repository to update.
     */
    fun project(repository: StoryRepository)
}

/**
 * Triggered by the Scout when a new story is identified from an external source (e.g., Hacker News).
 *
 * @property id The unique identifier from the source system.
 * @property title The headline of the story.
 * @property url The direct link to the story content.
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
 *
 * @property storyId References the original [StoryDiscovered.id].
 * @property cleanText The raw, fluff-free text ready for AI analysis.
 * @property errorText The error output from the scraper (if any).
 * @property exitCode The exit code of the scraper process.
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
    /** Articles, blog posts, essays, news stories. */
    WRITING,
    /** SaaS landing pages, commercial products, tools for sale, marketing materials. */
    PRODUCT,
    /** Technical demos, interactive experiments, "Show HN" style prototypes. */
    DEMO,
    /** Source code repositories (e.g., GitHub, GitLab). */
    SOURCE,
    /** Content that doesn't fit the above categories. */
    UNKNOWN
}

/**
 * Triggered by the Strategist after identifying the type of content.
 *
 * @property storyId References the original [StoryDiscovered.id].
 * @property category The identified type of content.
 * @property reasoning The AI's explanation for this categorization.
 */
data class StoryCategorized(
    val storyId: String,
    val category: StoryCategory,
    val reasoning: String,
    override val timestamp: Instant = Instant.now()
) : ProjectableEvent {
    override fun project(repository: StoryRepository) {
        repository.update(storyId) { it.copy(category = category) }
    }
}

/**
 * Triggered by the Memory domain in response to a context request.
 * Contains the aggregated personal "Source of Truth" for The Curator.
 *
 * @property content The aggregated local corpus (markdown files, RSS drafts, etc.).
 */
data class ContextResponse(
    val content: String,
    override val timestamp: Instant = Instant.now()
) : SlopEvent

/**
 * How the story aligns with the user's body of work.
 */
enum class Alignment {
    /** Reinforces what you already know/believe. */
    ECHO_CHAMBER,
    /** Challenges your existing views or provides a different perspective. */
    OPPOSITE_VIEW,
    /** Adds new information that fits well with your current knowledge. */
    COMPLEMENTARY
}

/**
 * The detected level of hype or "slop" in the content.
 */
enum class HypeRisk {
    /** Low signal of hype, looks like genuine insight. */
    LOW,
    /** Moderate buzzword usage or promotional tone. */
    MEDIUM,
    /** High-hype, low-signal content. */
    HIGH
}

/**
 * The final output of the Strategist (The Curator).
 * Contains the deep analysis and SECV scoring of a story.
 *
 * @property storyId References the original [StoryDiscovered.id].
 * @property mms Mental Model Shift score (1-10).
 * @property sa Strategic Actionability score (1-10).
 * @property sd Signal Density score (1-10).
 * @property d Durability score (1-10).
 * @property alignment How the story aligns with your body of work.
 * @property hypeRisk The detected level of hype.
 * @property sparringNote A personalized note explaining why this story matters (or doesn't).
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

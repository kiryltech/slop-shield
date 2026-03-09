package ai.slopshield.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.reflect.KClass
import java.io.Serializable as JavaSerializable

/**
 * Custom serializer for [Instant] to handle string conversion during JSON serialization.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

/**
 * Custom polymorphic serializer for [SlopEvent]s.
 * Uses the presence of specific keys in the JSON object to determine the subclass type.
 */
object SlopEventSerializer : JsonContentPolymorphicSerializer<SlopEvent>(SlopEvent::class) {
    override fun selectDeserializer(element: kotlinx.serialization.json.JsonElement) = when {
        "id" in element.jsonObject && "url" in element.jsonObject -> StoryDiscovered.serializer()
        "cleanText" in element.jsonObject && "exitCode" in element.jsonObject -> HarvestComplete.serializer()
        "category" in element.jsonObject && "reasoning" in element.jsonObject -> StoryCategorized.serializer()
        "content" in element.jsonObject -> ContextResponse.serializer()
        "mms" in element.jsonObject && "sparringNote" in element.jsonObject -> AnalysisComplete.serializer()
        else -> throw IllegalArgumentException("Unknown event type: $element")
    }
}

/**
 * Functional interface for evaluating whether a component should be enabled.
 */
interface EnabledIf {
    /**
     * @return True if the condition is met and the component should be enabled.
     */
    fun isEnabled(): Boolean
}

/**
 * Default implementation that checks a system property.
 *
 * @property property The system property name to check.
 * @property expected The expected value of the system property.
 */
class SystemPropertyCondition(
    private val property: String,
    private val expected: String
) : EnabledIf {
    override fun isEnabled(): Boolean = System.getProperty(property) == expected
}

/**
 * Specific condition for debug mode.
 * Checks if the 'slopshield.debug' system property is set to true.
 */
class DebugEnabledCondition : EnabledIf {
    override fun isEnabled() = java.lang.Boolean.getBoolean("slopshield.debug")
}

/**
 * Annotation to mark a class as an event listener.
 * Listeners typically implement [SlopHandler].
 */
@Target(AnnotationTarget.CLASS)
annotation class SlopListener

/**
 * Annotation to mark a class as a standalone domain service.
 * Services typically implement [SlopServiceLifecycle].
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
    /**
     * Called when the application context is started.
     */
    fun start()

    /**
     * Called when the application context is stopping.
     */
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
        get() = CachedTypeRegistry.get(this::class, SlopHandler::class)
            .first() as KClass<T>

    /**
     * Optional filter to further refine which events are processed.
     *
     * @param event The event to check.
     * @return True if the handler wants to process this event.
     */
    fun canHandle(event: T): Boolean = true

    /**
     * Processes the event.
     *
     * @param event The event to process.
     */
    suspend fun onEvent(event: T)
}

/**
 * Base interface for all events within the SlopShield Domain Event Stream.
 */
@Serializable
sealed interface SlopEvent {
    /**
     * The point in time when the event was created.
     */
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant
}

/**
 * Events related to system activity and handler execution.
 * These are used primarily for observability and UI dashboards.
 */
@Serializable
sealed interface ActivityEvent {
    /**
     * A unique identifier for the execution context of the activity.
     */
    val executionId: String
    
    /**
     * The number of active workers at the time of the event.
     */
    val activeWorkers: Int

    /**
     * The time when the activity occurred.
     */
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant
}

/**
 * Emitted when a handler starts processing an event.
 *
 * @property handler The name of the handler class.
 * @property event The name of the event class.
 * @property storyId The related story ID, if applicable.
 */
@Serializable
data class HandlerStarted(
    val handler: String,
    val event: String,
    val storyId: String? = null,
    override val activeWorkers: Int,
    override val executionId: String = java.util.UUID.randomUUID().toString(),
    @Serializable(with = InstantSerializer::class)
    override val timestamp: Instant = Instant.now()
) : ActivityEvent

/**
 * Emitted when a handler finishes processing an event.
 *
 * @property handler The name of the handler class.
 * @property event The name of the event class.
 * @property storyId The related story ID, if applicable.
 * @property elapsedMs The time taken to process the event in milliseconds.
 * @property success True if the handler executed without throwing an exception.
 */
@Serializable
data class HandlerFinished(
    val handler: String,
    val event: String,
    override val executionId: String,
    override val activeWorkers: Int,
    val storyId: String? = null,
    val elapsedMs: Long,
    val success: Boolean,
    @Serializable(with = InstantSerializer::class)
    override val timestamp: Instant = Instant.now()
) : ActivityEvent

/**
 * An event that knows how to project itself into the [StoryRepository].
 * This pattern localizes the mutation logic within the event itself.
 */
interface ProjectableEvent : SlopEvent {
    /**
     * Projects the event's data into the repository.
     *
     * @param repository The destination story repository.
     */
    fun project(repository: StoryRepository)
}

/**
 * Triggered by the Scout when a new story is identified from an external source (e.g., Hacker News).
 *
 * @property id The unique identifier from the source.
 * @property title The title of the story.
 * @property url The URL of the story.
 */
@Serializable
data class StoryDiscovered(
    val id: String,
    val title: String,
    val url: String,
    @Serializable(with = InstantSerializer::class)
    override val timestamp: Instant = Instant.now()
) : ProjectableEvent {
    override fun project(repository: StoryRepository) {
        repository.upsert(Story(id = id, title = title, url = url))
    }
}

/**
 * Triggered by the Harvester after successfully extracting and cleaning text from a story's URL.
 *
 * @property storyId The target story's ID.
 * @property cleanText The extracted text content, formatted as Markdown.
 * @property errorText Any standard error output from the scraping process.
 * @property exitCode The exit code from the scraping process.
 */
@Serializable
data class HarvestComplete(
    val storyId: String,
    val cleanText: String,
    val errorText: String,
    val exitCode: Int,
    @Serializable(with = InstantSerializer::class)
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
    /** Blog posts, articles, essays. */
    WRITING, 
    /** Commercial products, marketing materials. */
    PRODUCT, 
    /** Technical demos, prototypes. */
    DEMO, 
    /** Source code repositories or snippets. */
    SOURCE, 
    /** Video content. */
    VIDEO, 
    /** Content that does not fit into predefined categories. */
    UNKNOWN
}

/**
 * Triggered by the Strategist after identifying the type of content.
 *
 * @property storyId The related story ID.
 * @property category The identified category for the content.
 * @property reasoning The explanation for why this category was assigned.
 */
@Serializable
data class StoryCategorized(
    val storyId: String,
    val category: StoryCategory,
    val reasoning: String,
    @Serializable(with = InstantSerializer::class)
    override val timestamp: Instant = Instant.now()
) : ProjectableEvent {
    override fun project(repository: StoryRepository) {
        repository.update(storyId) { it.copy(category = category, categoryReasoning = reasoning) }
    }
}

/**
 * Triggered when a component needs the personal context for AI analysis.
 */
@Serializable
data class ContextRequest(
    @Serializable(with = InstantSerializer::class)
    override val timestamp: Instant = Instant.now()
) : SlopEvent

/**
 * Triggered by the Memory domain in response to a context request.
 *
 * @property content The raw context text loaded from the user's personal context directory.
 */
@Serializable
data class ContextResponse(
    val content: String,
    @Serializable(with = InstantSerializer::class)
    override val timestamp: Instant = Instant.now()
) : SlopEvent

/**
 * Represents how well a story aligns with the user's personal context.
 */
enum class Alignment {
    /** Restates existing views. */
    ECHO_CHAMBER, 
    /** Substantively challenges core philosophies. */
    OPPOSITE_VIEW, 
    /** Relates to interests and adds new, useful information. */
    COMPLEMENTARY, 
    /** High-quality but unrelated to personal themes. */
    ORTHOGONAL, 
    /** Low quality or pure noise. */
    IRRELEVANT
}

/**
 * Evaluates the level of hype or marketing fluff in the story.
 */
enum class HypeRisk {
    /** Evidence-based and technical. */
    LOW, 
    /** Has interesting ideas but contains "future-speak". */
    MEDIUM, 
    /** Buzzword-driven, lacks substance. */
    HIGH
}

/**
 * A structured reasoning point explaining the analysis outcome.
 *
 * @property title The title or thematic area of the bullet point.
 * @property description Detailed reasoning text.
 */
@Serializable
data class ReasoningBullet(
    val title: String,
    val description: String
) : JavaSerializable

/**
 * The final output of the Strategist (The Curator).
 *
 * @property storyId The analyzed story's ID.
 * @property mms Mental Model Shift score (0-10).
 * @property sa Strategic Actionability score (0-10).
 * @property sd Signal Density score (0-10).
 * @property d Durability score (0-10).
 * @property alignment The degree to which it aligns with the user's personal context.
 * @property hypeRisk The estimated hype factor of the content.
 * @property sparringNote A concise engineering perspective on the value of reading the story.
 * @property reasoningBullets Explanatory bullets detailing the analysis scores.
 */
@Serializable
data class AnalysisComplete(
    val storyId: String,
    val mms: Int,
    val sa: Int,
    val sd: Int,
    val d: Int,
    val alignment: Alignment,
    val hypeRisk: HypeRisk,
    val sparringNote: String,
    val reasoningBullets: List<ReasoningBullet> = emptyList(),
    @Serializable(with = InstantSerializer::class)
    override val timestamp: Instant = Instant.now()
) : ProjectableEvent, JavaSerializable {
    /**
     * The weighted average score derived from the SECV rubric.
     */
    val totalScore: Double get() = (mms + sa + sd + d) / 4.0

    override fun project(repository: StoryRepository) {
        repository.update(storyId) { it.copy(analysis = this) }
    }
}
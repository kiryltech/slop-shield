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
import java.io.Serializable as JavaSerializable
import java.time.Instant
import kotlin.reflect.KClass

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

/**
 * Custom polymorphic serializer for SlopEvents.
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
    override fun isEnabled() = java.lang.Boolean.getBoolean("slopshield.debug")
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
        get() = CachedTypeRegistry.get(this::class, SlopHandler::class)
            .first() as KClass<T>

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
@Serializable
sealed interface SlopEvent {
    /**
     * The point in time when the event was created.
     */
    @Serializable(with = InstantSerializer::class)
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
    WRITING, PRODUCT, DEMO, SOURCE, VIDEO, UNKNOWN
}

/**
 * Triggered by the Strategist after identifying the type of content.
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
 */
@Serializable
data class ContextResponse(
    val content: String,
    @Serializable(with = InstantSerializer::class)
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

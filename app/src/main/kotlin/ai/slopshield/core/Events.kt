package ai.slopshield.core

import java.time.Instant

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
) : SlopEvent

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
) : SlopEvent

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
) : SlopEvent

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
    LOW,
    MEDIUM,
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
) : SlopEvent {
    /**
     * The weighted average score derived from the SECV rubric.
     */
    val totalScore: Double get() = (mms + sa + sd + d) / 4.0
}

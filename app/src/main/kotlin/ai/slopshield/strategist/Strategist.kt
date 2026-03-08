package ai.slopshield.strategist

import ai.slopshield.core.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

@Serializable
data class DeepAnalysisResult(
    val mms: Int,
    val sa: Int,
    val sd: Int,
    val d: Int,
    val alignment: String,
    val hypeRisk: String,
    val sparringNote: String
)

/**
 * The Strategist (The Curator).
 * Performs deep SECV analysis on stories that have been categorized as high-signal.
 * It uses the personal context from MemoryService to calibrate its judgment.
 */
@SlopService
class Strategist(
    private val scope: CoroutineScope,
    private val eventStream: SharedFlow<SlopEvent>,
    private val collector: FlowCollector<SlopEvent>,
    private val aiService: AIService,
    private val repository: StoryRepository
) : SlopServiceLifecycle {

    private var currentContext: String = ""
    private var analysisJob: kotlinx.coroutines.Job? = null

    private fun generateInstructions(): String = """
        You are "The Curator", a highly opinionated and experienced Software Engineer.
        Your goal is to evaluate the technical story provided in the input through the lens of my PERSONAL CONTEXT (also provided in the input).

        Evaluate this story using the SECV Scoring Rubric (0-10):
        - MMS (Mental Model Shift): Does this change how I think, or just confirm what I know?
        - SA (Strategic Actionability): Can I make a concrete decision or take action based on this?
        - SD (Signal Density): Is it high-information "meat" or low-signal "fluff"?
        - D (Durability): Will this still be valuable in 2 years?

        Identify:
        - Alignment: Is it an ECHO_CHAMBER (restates my views), OPPOSITE_VIEW (challenges me), or COMPLEMENTARY?
        - HypeRisk: Is it LOW, MEDIUM, or HIGH risk of being empty hype?

        Provide a "Sparring Note": A concise, direct, and slightly cynical engineering perspective on why I should (or should not) read this, specifically contrasting it with my PERSONAL CONTEXT.

        CRITICAL: Output ONLY a raw JSON object.
        Format: {"mms": 0, "sa": 0, "sd": 0, "d": 0, "alignment": "ECHO_CHAMBER", "hypeRisk": "LOW", "sparringNote": "..."}
    """.trimIndent()

    private fun generateData(context: String, title: String, url: String, content: String): String = """
        --- PERSONAL CONTEXT (My Source of Truth) ---
        $context
        --- END PERSONAL CONTEXT ---

        --- STORY CONTENT ---
        Title: $title
        URL: $url
        Content:
        $content
        --- END STORY CONTENT ---
    """.trimIndent()

    override fun start() {
        logger.info { "Strategist: Starting Curator analysis engine..." }
        
        analysisJob = scope.launch {
            eventStream.collect { event ->
                when (event) {
                    is ContextResponse -> {
                        currentContext = event.content
                        logger.info { "Strategist: Personal context updated (${currentContext.length} chars)" }
                    }
                    is StoryCategorized -> {
                        if (shouldAnalyze(event)) {
                            launch { analyze(event) }
                        }
                    }
                    else -> { /* Ignore other events */ }
                }
            }
        }

        // Request initial context
        scope.launch {
            collector.emit(ContextRequest())
        }
    }

    override fun stop() {
        logger.info { "Strategist: Stopping Curator analysis engine..." }
        analysisJob?.cancel()
    }

    private fun shouldAnalyze(event: StoryCategorized): Boolean {
        return event.category == StoryCategory.WRITING || event.category == StoryCategory.DEMO || event.category == StoryCategory.VIDEO
    }

    private suspend fun analyze(event: StoryCategorized) {
        val story = repository.get(event.storyId) ?: return
        if (story.cleanText.isNullOrBlank()) {
            logger.warn { "Strategist: Cannot analyze story ${event.storyId} - no content available" }
            return
        }

        logger.info { "Strategist: Performing deep analysis on story: ${story.title}" }

        val instructions = generateInstructions()
        val data = generateData(
            context = currentContext,
            title = story.title,
            url = story.url,
            content = story.cleanText
        )

        try {
            val result = aiService.process(instructions, data)
            if (result.exitCode == 0) {
                val jsonContent = sanitizeJson(result.stdout)
                val parsed = Json.decodeFromString<DeepAnalysisResult>(jsonContent)
                
                collector.emit(
                    AnalysisComplete(
                        storyId = event.storyId,
                        mms = parsed.mms,
                        sa = parsed.sa,
                        sd = parsed.sd,
                        d = parsed.d,
                        alignment = try { Alignment.valueOf(parsed.alignment.uppercase()) } catch (e: Exception) { Alignment.COMPLEMENTARY },
                        hypeRisk = try { HypeRisk.valueOf(parsed.hypeRisk.uppercase()) } catch (e: Exception) { HypeRisk.MEDIUM },
                        sparringNote = parsed.sparringNote
                    )
                )
                logger.info { "Strategist: Analysis complete for ${story.title}. Score: ${(parsed.mms + parsed.sa + parsed.sd + parsed.d) / 4.0}/10" }
            } else {
                logger.warn { "Strategist: AI analysis failed for story ${event.storyId} with exit code ${result.exitCode}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Strategist: Error during analysis of story ${event.storyId}" }
        }
    }

    private fun sanitizeJson(input: String): String {
        val jsonRegex = """(?s)\{.*\}""".toRegex()
        return jsonRegex.find(input)?.value ?: input
    }
}

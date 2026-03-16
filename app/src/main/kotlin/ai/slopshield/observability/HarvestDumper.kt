package ai.slopshield.observability

import ai.slopshield.core.DebugEnabledCondition
import ai.slopshield.core.Enabled
import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.SlopHandler
import ai.slopshield.core.SlopListener
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * A debug component that dumps harvested story content to disk.
 * This is primarily used for troubleshooting scraping issues and observing the 
 * raw input before it gets passed to the AI analysis.
 * Enabled only when debug mode is active.
 */
@SlopListener
@Enabled(DebugEnabledCondition::class)
class HarvestDumper : SlopHandler<HarvestComplete> {
    
    private val pid = ProcessHandle.current().pid()
    private val defaultPath = "/tmp/slop-shield-$pid"
    private val dumpDir = File(System.getProperty("slopshield.dump.path", defaultPath))

    init {
        if (!dumpDir.exists()) {
            dumpDir.mkdirs()
        }
        logger.info { "HarvestDumper: Debug dumper target directory: ${dumpDir.absolutePath}" }
    }

    /**
     * Handles the [HarvestComplete] event by writing the cleaned text to the file system.
     *
     * @param event The event containing the harvested text.
     */
    override suspend fun onEvent(event: HarvestComplete) {
        try {
            val baseFile = File(dumpDir, event.storyId)
            File("${baseFile.absolutePath}.stdout.md").writeText(event.cleanText)
            logger.debug { "HarvestDumper: Dumped story ${event.storyId} (success: ${event.success}) to ${dumpDir.absolutePath}" }
        } catch (e: Exception) {
            logger.error(e) { "HarvestDumper: Failed to dump story ${event.storyId}" }
        }
    }
}
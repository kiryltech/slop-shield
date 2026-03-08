package ai.slopshield.observability

import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.SlopHandler
import ai.slopshield.core.SlopListener
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.lang.ProcessHandle

private val logger = KotlinLogging.logger {}

/**
 * A debug component that dumps harvested story content to disk.
 */
@SlopListener
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

    override suspend fun onEvent(event: HarvestComplete) {
        try {
            val baseFile = File(dumpDir, event.storyId)
            File("${baseFile.absolutePath}.stdout.md").writeText(event.cleanText)
            if (event.errorText.isNotBlank()) {
                File("${baseFile.absolutePath}.stderr.log").writeText(event.errorText)
            }
            logger.debug { "HarvestDumper: Dumped story ${event.storyId} (exit: ${event.exitCode}) to ${dumpDir.absolutePath}" }
        } catch (e: Exception) {
            logger.error(e) { "HarvestDumper: Failed to dump story ${event.storyId}" }
        }
    }
}

package ai.slopshield.observability

import ai.slopshield.core.SlopService
import ai.slopshield.core.SlopServiceLifecycle
import ai.slopshield.core.StoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title

private val logger = KotlinLogging.logger {}

/**
 * A minimalist Web UI to view the contents of the StoryRepository.
 */
@SlopService
class WebService(
    private val repository: StoryRepository,
    private val port: Int = System.getProperty("slopshield.web.port", "8080").toInt()
) : SlopServiceLifecycle {
    private var server: NettyApplicationEngine? = null

    override fun start() {
        logger.info { "WebService: Starting Web UI on port $port..." }
        
        server = embeddedServer(Netty, port = port) {
            routing {
                get("/") {
                    val stories = repository.getAll().sortedByDescending { it.id }.toList()
                    call.respondHtml {
                        head {
                            title("SlopShield Dashboard")
                            style {
                                +"""
                                    body { font-family: sans-serif; line-height: 1.6; max-width: 1000px; margin: 0 auto; padding: 20px; background: #f4f4f9; }
                                    h1 { color: #333; border-bottom: 2px solid #ccc; padding-bottom: 10px; }
                                    .story-card { background: white; border-radius: 8px; padding: 15px; margin-bottom: 20px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
                                    .story-title { font-size: 1.2em; font-weight: bold; color: #0066cc; text-decoration: none; }
                                    .story-title:hover { text-decoration: underline; }
                                    .meta { font-size: 0.9em; color: #666; margin-top: 5px; }
                                    .badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 0.8em; font-weight: bold; text-transform: uppercase; margin-right: 5px; }
                                    .writing { background: #e3f2fd; color: #0d47a1; }
                                    .product { background: #f1f8e9; color: #1b5e20; }
                                    .demo { background: #fff3e0; color: #e65100; }
                                    .source { background: #f3e5f5; color: #4a148c; }
                                    .unknown { background: #eeeeee; color: #424242; }
                                    .reasoning { font-style: italic; font-size: 0.9em; color: #555; margin-top: 10px; border-left: 3px solid #ddd; padding-left: 10px; }
                                """
                            }
                        }
                        body {
                            h1 { +"🛡️ SlopShield Dashboard" }
                            p { +"Total stories in database: ${stories.size}" }
                            
                            stories.forEach { story ->
                                div("story-card") {
                                    a(href = story.url, target = "_blank", classes = "story-title") {
                                        +story.title
                                    }
                                    div("meta") {
                                        span("badge ${story.category?.name?.lowercase() ?: "unknown"}") {
                                            +(story.category?.name ?: "UNCATEGORIZED")
                                        }
                                        +" | ID: ${story.id}"
                                    }
                                    val infoText = story.analysis?.sparringNote ?: story.categoryReasoning
                                    infoText?.let {
                                        div("reasoning") {
                                            +it
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    override fun stop() {
        logger.info { "WebService: Stopping Web UI..." }
        server?.stop(1000, 5000)
    }
}

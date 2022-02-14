package io.github.gnuf0rce.github

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import java.time.*

class RateLimitFeature internal constructor(val send: suspend (Status, String) -> Unit) {
    data class Status(val limit: Int, val remaining: Int, val reset: Long)

    data class Config(var notice: suspend (rate: Status, resource: String) -> Unit = { _, _ -> })

    constructor(config: Config) : this(config.notice)

    private val rates: MutableMap<String, Status> = HashMap<String, Status>()
        .withDefault { Status(limit = 1, remaining = 1, reset = OffsetDateTime.now().toEpochSecond()) }

    private val mutex = Mutex()

    private fun HttpMessage.rate(): Status? {
        return Status(
            limit = headers["X-RateLimit-Limit"]?.toInt() ?: return null,
            remaining = headers["X-RateLimit-Remaining"]?.toInt() ?: return null,
            reset = headers["X-RateLimit-Reset"]?.toLong() ?: return null,
        )
    }

    private suspend fun Status.delay() {
        delay((reset - OffsetDateTime.now().toEpochSecond()) * 1_000)
    }

    companion object Feature : HttpClientFeature<Config, RateLimitFeature> {
        val resource: AttributeKey<String> = AttributeKey("RateLimitResource")

        override val key: AttributeKey<RateLimitFeature> = AttributeKey("RateLimit")

        override fun prepare(block: Config.() -> Unit): RateLimitFeature = RateLimitFeature(Config().apply(block))

        override fun install(feature: RateLimitFeature, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.Before) {
                val type = context.attributes.computeIfAbsent(resource) { "rate" }
                with(feature) {
                    mutex.withLock {
                        val rate = rates.getValue(type)
                        if (rate.remaining == 0) {
                            rate.delay()
                        }
                    }
                }
            }

            scope.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                val type = response.request.attributes[resource]
                with(feature) {
                    mutex.withLock {
                        val rate = response.rate() ?: return@withLock
                        rates[type] = rate
                        send(rate, type)
                    }
                }
            }
        }
    }
}

@Suppress("FunctionName")
fun HttpClientConfig<*>.RateLimit(block: RateLimitFeature.Config.() -> Unit) {
    install(RateLimitFeature, block)
}
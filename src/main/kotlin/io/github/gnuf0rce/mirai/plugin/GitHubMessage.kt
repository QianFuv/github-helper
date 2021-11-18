@file:OptIn(MiraiExperimentalApi::class, ConsoleExperimentalApi::class)

package io.github.gnuf0rce.mirai.plugin

import io.github.gnuf0rce.github.*
import io.github.gnuf0rce.github.entry.*
import io.github.gnuf0rce.mirai.plugin.data.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.*
import net.mamoe.mirai.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.console.util.ContactUtils.getContactOrNull
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.*
import java.io.File
import java.time.*

internal fun Contact(id: Long): Contact = Bot.instances.firstNotNullOf { it.getContactOrNull(id) }

@Serializable
enum class MessageType { TEXT, XML, JSON }

internal suspend fun UserInfo.avatar(flush: Boolean = false, client: GitHubClient = github): File {
    val url = Url(avatarUrl)
    return ImageFolder.resolve("avatar").resolve(url.filename).apply {
        if (exists().not() || flush) {
            parentFile.mkdirs()
            writeBytes(client.useHttpClient { client ->
                client.get(url)
            })
        }
    }
}

private const val STATS_API = "https://github-readme-stats.vercel.app/api"

private val RANK_REGEX = """(?<=rank-text[^>]{0,1024}>[^>]{0,1024}>[^\w]{0,1024})[\w+-]+""".toRegex()

private val STARS_REGEX = """(?<=stars[^>]{0,1024}>[^\w]{0,1024})[^<\s]+""".toRegex()

private val COMMITS_REGEX = """(?<=commits[^>]{0,1024}>[^\w]{0,1024})[^<\s]+""".toRegex()

private val PRS_REGEX = """(?<=prs[^>]{0,1024}>[^\w]{0,1024})[^<\s]+""".toRegex()

private val ISSUES_REGEX = """(?<=issues[^>]{0,1024}>[^\w]{0,1024})[^<\s]+""".toRegex()

private val CONTRIB_REGEX = """(?<=contribs[^>]{0,1024}>[^\w]{0,1024})[^<\s]+""".toRegex()

data class UserStats(
    /**
     * B+, A+, A++, S, S+
     */
    val rank: String?,
    val stars: String?,
    val commits: String?,
    val prs: String?,
    val issues: String?,
    val contrib: String?
)

/**
 * XXX: svg to text
 */
@Suppress("BlockingMethodInNonBlockingContext")
internal suspend fun UserInfo.stats(flush: Boolean = false, client: GitHubClient = github): UserStats {
    val svg = ImageFolder.resolve("stats").resolve("${login}.svg").apply {
        if (exists().not() || flush) {
            parentFile.mkdirs()
            writeBytes(client.useHttpClient { client ->
                client.get(STATS_API) {
                    parameter("username", login)
                    for ((key, value) in GitHubConfig.stats) parameter(key, value)
                }
            })
        }
    }

    val xml = svg.readText()

    return UserStats(
        rank = RANK_REGEX.find(xml)?.value,
        stars = STARS_REGEX.find(xml)?.value,
        commits = COMMITS_REGEX.find(xml)?.value,
        prs = PRS_REGEX.find(xml)?.value,
        issues = ISSUES_REGEX.find(xml)?.value,
        contrib = CONTRIB_REGEX.find(xml)?.value
    )
}

internal fun MessageChainBuilder.appendLine(image: Image) = append(image).appendLine()

/**
 * TODO: more info ...
 */
suspend fun Owner.toMessage(contact: Contact): Message {
    return when (type) {
        Owner.Type.User -> {
            val stats = stats()
            val year = Year.now()
            val text = buildString {
                appendLine("${login}'s GitHub Stats")
                appendLine("Rank:                 ${stats.rank}")
                appendLine("Total Stars Earned:   ${stats.stars}")
                appendLine("Total Commits (${year}): ${stats.commits}")
                appendLine("Total PRs:            ${stats.prs}")
                appendLine("Total Issues:         ${stats.issues}")
                appendLine("Contributed to:       ${stats.contrib}")
            }
            avatar().uploadAsImage(contact) + "\n" + text
        }
        Owner.Type.Organization -> avatar().uploadAsImage(contact)
    }
}

suspend fun HtmlPage.toMessage(contact: Contact, type: MessageType, notice: String): Message {
    return when (this) {
        is Issue -> toMessage(contact, type, notice)
        is Pull -> toMessage(contact, type, notice)
        is Release -> toMessage(contact, type, notice)
        is Commit -> toMessage(contact, type, notice)
        is Repo -> toMessage(contact, type, notice)
        is Milestone -> toMessage(contact, type, notice)
        is Owner -> toMessage(contact)
        is License -> (htmlUrl ?: name).toPlainText()
        is Issue.PullRequest -> htmlUrl.toPlainText()
        is Readme -> htmlUrl.toPlainText()
        is Team -> htmlUrl.toPlainText()
        is Commit.Tree -> (htmlUrl ?: sha).toPlainText()
    }
}

suspend fun Contact.sendMessage(entry: HtmlPage, notice: String) = sendMessage(entry.toMessage(this, reply, notice))

suspend fun ControlRecord.toMessage(contact: Contact, type: MessageType, notice: String): Message {
    val image = user.avatar().uploadAsImage(contact)
    return when (type) {
        MessageType.TEXT -> buildMessageChain {
            appendLine(image)
            appendLine("$notice with issue by ${user.login} ")
            appendLine("URL: $htmlUrl ")
            appendLine("CREATED_AT: $createdAt ")
            appendLine("UPDATED_AT: $updatedAt ")
            appendLine("TITLE: $title ")
            appendLine("STATE: $state ")
            if (labels.isNotEmpty()) appendLine("LABELS: ${labels.joinToString { it.name }} ")
        }
        MessageType.XML -> buildXmlMessage(1) {
            actionData = htmlUrl
            templateId = -1
            action = "web"
            brief = notice
            flag = 0

            item {
                layout = 2
                picture(coverUrl = image.queryUrl())
                title(text = title)
                for (label in labels) {
                    summary(text = label.name, color = "#${label.color.uppercase()}")
                }
            }

            source(name = notice)
        }
        MessageType.JSON -> buildStructMessage<StructNew> {
            config.ctime = createdAt.toEpochSecond()
            config.token = "239525e4c0fc9b6849624417086250df"
            desc = "issue"
            uin = contact.id
            detail.appType = 1
            detail.appid = 100951776
            detail.desc = labels.joinToString { it.name }
            detail.jumpUrl = htmlUrl
            detail.preview = image.queryUrl()
            detail.tag = "GitHub"
            detail.title = title
            prompt = "[分享]${title}"
        }
    }
}

suspend fun Release.toMessage(contact: Contact, type: MessageType, notice: String): Message {
    val image = author.avatar().uploadAsImage(contact)
    return when (type) {
        MessageType.TEXT -> buildMessageChain {
            appendLine(image)
            appendLine("$notice with release by ${author.login} ")
            appendLine("CREATED_AT: $createdAt ")
            appendLine("PUBLISHED_AT: $publishedAt ")
            appendLine("URL: $htmlUrl ")
            appendLine("NAME: $name ")
            appendLine(body)
        }
        MessageType.XML -> buildXmlMessage(1) {
            actionData = htmlUrl
            templateId = -1
            action = "web"
            brief = notice
            flag = 0

            item {
                layout = 2
                picture(coverUrl = image.queryUrl())
                title(text = name)
                for (asset in assets) {
                    summary(text = asset.name)
                }
                summary(text = tagName)
            }

            source(name = notice)
        }
        MessageType.JSON -> TODO()
    }
}

suspend fun Commit.toMessage(contact: Contact, type: MessageType, notice: String): Message {
    val image = author.avatar().uploadAsImage(contact)
    return when (type) {
        MessageType.TEXT -> buildMessageChain {
            appendLine(image)
            appendLine("$notice with commit by ${author.login} ")
            appendLine("URL: $htmlUrl ")
            appendLine("CREATED_AT: $createdAt ")
            appendLine(detail.message)
        }
        MessageType.XML -> buildXmlMessage(1) {
            actionData = htmlUrl
            templateId = -1
            action = "web"
            brief = notice
            flag = 0

            item {
                layout = 2
                picture(coverUrl = image.queryUrl())
                title(text = sha)
                summary(text = detail.message)
            }

            source(name = notice)
        }
        MessageType.JSON -> TODO()
    }
}

suspend fun Repo.toMessage(contact: Contact, type: MessageType, notice: String): Message {
    val image = owner.avatar().uploadAsImage(contact)
    return when (type) {
        MessageType.TEXT -> buildMessageChain {
            appendLine(image)
            appendLine("$notice with repo by ${owner.login} ")
            appendLine("URL: $htmlUrl ")
            appendLine("CREATED_AT: $createdAt ")
            appendLine("LANGUAGE: $language ")
            appendLine("DESCRIPTION: $description ")
        }
        MessageType.XML -> buildXmlMessage(1) {
            actionData = htmlUrl
            templateId = -1
            action = "web"
            brief = notice
            flag = 0

            item {
                layout = 2
                picture(coverUrl = image.queryUrl())
                title(text = name)
                for (topic in topics) {
                    summary(text = topic)
                }
            }

            source(name = notice)
        }
        MessageType.JSON -> TODO()
    }
}

suspend fun Milestone.toMessage(contact: Contact, type: MessageType, notice: String): Message {
    val image = creator.avatar().uploadAsImage(contact)
    return when (type) {
        MessageType.TEXT -> buildMessageChain {
            appendLine(image)
            appendLine("$notice with milestone by ${creator.login} ")
            appendLine("URL: $htmlUrl ")
            appendLine("CREATED_AT: $createdAt ")
            appendLine("CREATED_AT: $updatedAt ")
            appendLine("TITLE: $title ")
            appendLine("STATE: $state ")
            appendLine("DESCRIPTION: $description ")
        }
        MessageType.XML -> buildXmlMessage(1) {
            actionData = htmlUrl
            templateId = -1
            action = "web"
            brief = notice
            flag = 0

            item {
                layout = 2
                picture(coverUrl = image.queryUrl())
                title(text = title)
                summary(text = description.orEmpty())
            }

            source(name = notice)
        }
        MessageType.JSON -> TODO()
    }
}
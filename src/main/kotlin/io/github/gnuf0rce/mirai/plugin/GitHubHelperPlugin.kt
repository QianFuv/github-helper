package io.github.gnuf0rce.mirai.plugin

import io.github.gnuf0rce.mirai.plugin.command.*
import io.github.gnuf0rce.mirai.plugin.data.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.event.*
import net.mamoe.mirai.event.events.*

object GitHubHelperPlugin : KotlinPlugin(
    JvmPluginDescription(
        id = "io.github.gnuf0rce.github-helper",
        name = "github-helper",
        version = "1.1.0",
    ) {
        author("cssxsh")
    }
) {
    override fun onEnable() {
        GitHubConfig.reload()
        GitHubRepoTaskData.reload()
        GitHubTaskData.reload()

        GitHubRepoIssueCommand.register()
        GitHubRepoPullCommand.register()
        GitHubRepoReleaseCommand.register()
        GitHubRepoCommitCommand.register()
        GitHubIssuesCommand.register()

        globalEventChannel().subscribeOnce<BotOnlineEvent> {
            GitHubRepoIssueCommand.subscriber.start()
            GitHubRepoPullCommand.subscriber.start()
            GitHubRepoReleaseCommand.subscriber.start()
            GitHubRepoCommitCommand.subscriber.start()
            GitHubIssuesCommand.subscriber.start()
        }
    }

    override fun onDisable() {
        GitHubRepoIssueCommand.unregister()
        GitHubRepoIssueCommand.subscriber.stop()
        GitHubRepoPullCommand.unregister()
        GitHubRepoPullCommand.subscriber.stop()
        GitHubRepoReleaseCommand.unregister()
        GitHubRepoReleaseCommand.subscriber.stop()
        GitHubRepoCommitCommand.unregister()
        GitHubRepoCommitCommand.subscriber.stop()
    }
}
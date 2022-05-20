/*
 * Copyright 2021-2022 dsstudio Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/gnuf0rce/github-helper/blob/master/LICENSE
 */


package io.github.gnuf0rce.github.entry

import kotlinx.serialization.*
import java.time.*

@Serializable
public data class IssueEvent(
    @SerialName("actor")
    override val actor: User?,
    @SerialName("commit_id")
    val commitId: String?,
    @SerialName("commit_url")
    val commitUrl: String?,
    @Contextual
    @SerialName("created_at")
    override val createdAt: OffsetDateTime,
    @SerialName("event")
    val event: String,
    @SerialName("id")
    val id: Long,
    @SerialName("issue")
    val issue: Issue? = null,
    @SerialName("label")
    val label: Label? = null,
    @SerialName("assignee")
    val assignee: User? = null,
    @SerialName("assigner")
    val assigner: User? = null,
    @SerialName("review_requester")
    val reviewRequester: User? = null,
    @SerialName("requested_reviewer")
    val requestedReviewer: User? = null,
    @SerialName("requested_team")
    val requestedTeam: Team? = null,
    @SerialName("dismissed_review")
    val dismissedReview: Review? = null,
    @SerialName("milestone")
    val milestone: Milestone? = null,
    @SerialName("project_card")
    val projectCard: Card? = null,
    @SerialName("rename")
    val rename: Rename? = null,
    @SerialName("node_id")
    override val nodeId: String,
    @SerialName("url")
    override val url: String,
    @SerialName("author_association")
    val association: Association? = null,
    @SerialName("lock_reason")
    val lockReason: String? = null,
    @SerialName("state_reason")
    val stateReason: String? = null,
    @SerialName("performed_via_github_app")
    val performedViaGithubApp: GithubAppInfo? = null
) : Entry, Event {

    override val updatedAt: OffsetDateTime
        get() = issue?.updatedAt ?: createdAt

    override val mergedAt: OffsetDateTime?
        get() = issue?.mergedAt

    override val closedAt: OffsetDateTime?
        get() = issue?.closedAt

    @Serializable
    public data class Label(
        @SerialName("color")
        val color: String?,
        @SerialName("name")
        val name: String?
    )

    @Serializable
    public data class Review(
        @SerialName("state")
        val state: String,
        @SerialName("review_id")
        val reviewId: Long,
        @SerialName("dismissal_message")
        val dismissalMessage: String? = null,
        @SerialName("dismissal_commit_id")
        val dismissalMessageId: String? = null
    )

    @Serializable
    public data class Milestone(
        @SerialName("title")
        val title: String
    )

    @Serializable
    public data class Card(
        @SerialName("url")
        val url: String,
        @SerialName("id")
        val id: Long,
        @SerialName("project_url")
        val projectUrl: String,
        @SerialName("project_id")
        val projectId: String,
        @SerialName("column_name")
        val columnName: String,
        @SerialName("previous_column_name")
        val previousColumnName: String? = null
    )

    @Serializable
    public data class Rename(
        @SerialName("from")
        val from: String,
        @SerialName("to")
        val to: String
    )
}
package io.github.mojira.arisa.modules

import arrow.core.Either
import arrow.core.extensions.fx
import arrow.core.left
import arrow.core.right
import io.github.mojira.arisa.domain.ChangeLogItem
import io.github.mojira.arisa.domain.Comment
import io.github.mojira.arisa.domain.Issue
import java.time.Instant

class KeepPlatformModule(
    private val keepPlatformTag: String
) : Module() {
    override fun execute(issue: Issue, lastRun: Instant): Either<ModuleError, ModuleResponse> = with(issue) {
        Either.fx {
            val platformChangeItems = changeLog.filter(::isPlatformChange)
            assertNotEmpty(platformChangeItems).bind()
            assertContainsKeepPlatformTag(comments).bind()
            val markedTime = comments.first(::isKeepPlatformTag).created
            val currentPlatform = platform.getOrDefault("None")
            val savedPlatform = platformChangeItems.getSavedValue(markedTime)
            assertNotNull(savedPlatform).bind()
            assertNotEquals(currentPlatform, savedPlatform).bind()

            platform = savedPlatform!!
        }
    }

    private fun isPlatformChange(item: ChangeLogItem) =
        item.field == "Platform"

    private fun changedByVolunteer(item: ChangeLogItem) =
            item.author.groups.any { it == "helper" || it == "global-moderators" || it == "staff" }

    private fun assertContainsKeepPlatformTag(comments: List<Comment>): Either<ModuleError, ModuleResponse> {
        val volunteerComments = comments.filter(::isVolunteerComment)
        return when {
            volunteerComments.any(::isKeepPlatformTag) -> Unit.right()
            else -> OperationNotNeededModuleResponse.left()
        }
    }

    private fun isVolunteerComment(comment: Comment) = comment.visibilityType == "group" &&
        comment.visibilityValue == "staff"

    private fun isKeepPlatformTag(comment: Comment) =
        comment.body.contains(keepPlatformTag)

    private fun List<ChangeLogItem>.getSavedValue(markedTime: Instant): String? {
        val volunteerChange = this.lastOrNull(::changedByVolunteer)
        //           last change by volunteer after markedTime
        return if (volunteerChange != null && volunteerChange.created.isAfter(markedTime)) {
            volunteerChange.changedToString.getOrDefault("None")
        } else {
            // what was first changed from after marked time
            val userChange = firstOrNull {
                it.created.isAfter(markedTime)
            }
            userChange?.changedFromString?.getOrDefault("None")
        }
    }
}

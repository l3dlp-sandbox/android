package mega.privacy.android.app.presentation.meeting.chat.model.messages.normal

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import mega.privacy.android.app.presentation.meeting.chat.extension.canForward
import mega.privacy.android.app.presentation.meeting.chat.model.messages.AvatarMessage
import mega.privacy.android.domain.entity.chat.messages.normal.ContactLinkMessage

/**
 * Contact link ui message
 * @property message Contact link message
 *
 */
data class ContactLinkUiMessage(
    val message: ContactLinkMessage,
    override val showAvatar: Boolean,
    override val showTime: Boolean,
    override val showDate: Boolean,
) : AvatarMessage() {
    override val contentComposable: @Composable (RowScope.() -> Unit) = {
        Text(text = "This is contact link message ${message.contactLink}") // placeholder
    }
    override val displayAsMine = message.isMine
    override val canForward = message.canForward
    override val timeSent = message.time
    override val userHandle = message.userHandle
    override val id = message.msgId
}
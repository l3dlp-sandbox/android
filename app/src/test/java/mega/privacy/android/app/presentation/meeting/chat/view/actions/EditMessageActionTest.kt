package mega.privacy.android.app.presentation.meeting.chat.view.actions

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth
import mega.privacy.android.app.presentation.meeting.chat.model.ChatViewModel
import mega.privacy.android.app.presentation.meeting.chat.view.CHAT_LOCATION_VIEW_TAG
import mega.privacy.android.app.presentation.meeting.chat.view.sheet.options.CHAT_BOTTOM_SHEET_OPTION_EDIT_TAG
import mega.privacy.android.domain.entity.chat.messages.meta.LocationMessage
import mega.privacy.android.domain.entity.chat.messages.normal.NormalMessage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class EditMessageActionTest {

    @get:Rule
    var composeTestRule = createComposeRule()

    private lateinit var underTest: EditMessageAction

    private val chatViewModel = mock<ChatViewModel>()

    @Before
    fun setUp() {
        underTest = EditMessageAction(
            chatViewModel = chatViewModel,
        )
    }

    @Test
    fun `test that action applies to editable, non location messages`() {
        Truth.assertThat(underTest.appliesTo(setOf(mock<NormalMessage> {
            on { isEditable } doReturn true
        }))).isTrue()
    }

    @Test
    fun `test that action does not apply to editable, location messages`() {
        Truth.assertThat(underTest.appliesTo(setOf(mock<LocationMessage> {
            on { isEditable } doReturn true
        }))).isFalse()
    }

    @Test
    fun `test that action does not apply to non editable messages`() {
        Truth.assertThat(underTest.appliesTo(setOf(mock<NormalMessage> {
            on { isEditable } doReturn false
        }))).isFalse()
    }

    @Test
    fun `test that composable contains edit bottom action`() {
        composeTestRule.setContent(
            underTest.bottomSheetMenuItem(
                messages = emptySet(),
                context = mock(),
            ) {}
        )

        composeTestRule.onNodeWithTag(CHAT_BOTTOM_SHEET_OPTION_EDIT_TAG).assertExists()
    }

    @Test
    fun `test that clicking the menu option invokes view model`() {
        val context = mock<Context>()
        val message = mock<LocationMessage> {
            on { isEditable } doReturn true
        }
        val messages = setOf(message)
        composeTestRule.setContent(
            underTest.bottomSheetMenuItem(
                messages = messages,
                context = context,
                hideBottomSheet = {},
            )
        )

        composeTestRule.onNodeWithTag(CHAT_BOTTOM_SHEET_OPTION_EDIT_TAG).performClick()
        verify(chatViewModel).onEditMessage(messages.first())
    }
}
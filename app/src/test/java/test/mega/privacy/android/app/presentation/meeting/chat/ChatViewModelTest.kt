package test.mega.privacy.android.app.presentation.meeting.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mega.privacy.android.app.presentation.meeting.chat.ChatViewModel
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.domain.entity.chat.ChatRoom
import mega.privacy.android.domain.entity.chat.ChatRoomChange
import mega.privacy.android.domain.usecase.GetChatRoom
import mega.privacy.android.domain.usecase.MonitorChatRoomUpdates
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ChatViewModelTest {
    private lateinit var underTest: ChatViewModel
    private val getChatRoomUseCase: GetChatRoom = mock()
    private val savedStateHandle: SavedStateHandle = mock()
    private val monitorChatRoomUpdates: MonitorChatRoomUpdates = mock()

    @BeforeAll
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        initTestClass()
    }

    @AfterAll
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @BeforeEach
    fun resetMocks() {
        reset(getChatRoomUseCase, savedStateHandle, monitorChatRoomUpdates)
    }

    private fun initTestClass() {
        underTest = ChatViewModel(
            getChatRoomUseCase = getChatRoomUseCase,
            monitorChatRoomUpdates = monitorChatRoomUpdates,
            savedStateHandle = savedStateHandle
        )
    }

    @Test
    fun `test that title update when we passing the chatId`() = runTest {
        val chatId = 123L
        val chatRoom = mock<ChatRoom>() {
            on { title } doReturn "title"
        }
        whenever(savedStateHandle.get<Long>(Constants.CHAT_ID)).thenReturn(chatId)
        whenever(getChatRoomUseCase(chatId)).thenReturn(chatRoom)
        initTestClass()
        underTest.state.test {
            Truth.assertThat(awaitItem().title).isEqualTo("title")
        }
    }

    @Test
    fun `test that title not update when we passing the chatId`() = runTest {
        whenever(savedStateHandle.get<Long>(Constants.CHAT_ID)).thenReturn(null)
        initTestClass()
        verifyNoInteractions(getChatRoomUseCase)
    }

    @Test
    fun `test that title update when chat room update with title change`() = runTest {
        val chatId = 123L
        val chatRoom = mock<ChatRoom>() {
            on { title } doReturn "title"
        }
        val updateFlow = MutableSharedFlow<ChatRoom>()
        whenever(savedStateHandle.get<Long>(Constants.CHAT_ID)).thenReturn(chatId)
        whenever(getChatRoomUseCase(chatId)).thenReturn(chatRoom)
        whenever(monitorChatRoomUpdates(chatId)).thenReturn(updateFlow)
        initTestClass()
        underTest.state.test {
            Truth.assertThat(awaitItem().title).isEqualTo("title")
        }
        val newTitle = "new title"
        val newChatRoom = mock<ChatRoom> {
            on { title } doReturn newTitle
            on { changes } doReturn listOf(ChatRoomChange.Title)
        }
        updateFlow.emit(newChatRoom)
        underTest.state.test {
            Truth.assertThat(awaitItem().title).isEqualTo(newTitle)
        }
    }
}
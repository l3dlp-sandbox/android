package mega.privacy.android.app.presentation.contact

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mega.privacy.android.app.components.ChatManagement
import mega.privacy.android.app.contacts.usecase.GetChatRoomUseCase
import mega.privacy.android.app.domain.usecase.CreateShareKey
import mega.privacy.android.app.meeting.gateway.CameraGateway
import mega.privacy.android.app.namecollision.usecase.CheckNameCollisionUseCase
import mega.privacy.android.app.objects.PasscodeManagement
import mega.privacy.android.app.presentation.contactinfo.ContactInfoViewModel
import mega.privacy.android.app.presentation.copynode.mapper.CopyRequestMessageMapper
import mega.privacy.android.app.usecase.CopyNodeUseCase
import mega.privacy.android.data.gateway.api.MegaChatApiGateway
import mega.privacy.android.domain.entity.EventType
import mega.privacy.android.domain.entity.StorageState
import mega.privacy.android.domain.entity.StorageStateEvent
import mega.privacy.android.domain.entity.chat.ChatRoom
import mega.privacy.android.domain.entity.contacts.ContactData
import mega.privacy.android.domain.entity.contacts.ContactItem
import mega.privacy.android.domain.entity.contacts.UserStatus
import mega.privacy.android.domain.entity.node.FolderNode
import mega.privacy.android.domain.entity.node.Node
import mega.privacy.android.domain.entity.node.NodeChanges
import mega.privacy.android.domain.entity.node.NodeUpdate
import mega.privacy.android.domain.entity.node.UnTypedNode
import mega.privacy.android.domain.entity.user.UserVisibility
import mega.privacy.android.domain.usecase.GetChatRoom
import mega.privacy.android.domain.usecase.MonitorContactUpdates
import mega.privacy.android.domain.usecase.RequestLastGreen
import mega.privacy.android.domain.usecase.account.MonitorStorageStateEventUseCase
import mega.privacy.android.domain.usecase.chat.CreateChatRoomUseCase
import mega.privacy.android.domain.usecase.chat.GetChatRoomByUserUseCase
import mega.privacy.android.domain.usecase.chat.StartConversationUseCase
import mega.privacy.android.domain.usecase.contact.ApplyContactUpdatesUseCase
import mega.privacy.android.domain.usecase.contact.GetContactFromChatUseCase
import mega.privacy.android.domain.usecase.contact.GetContactFromEmailUseCase
import mega.privacy.android.domain.usecase.contact.GetUserOnlineStatusByHandleUseCase
import mega.privacy.android.domain.usecase.contact.RemoveContactByEmailUseCase
import mega.privacy.android.domain.usecase.contact.SetUserAliasUseCase
import mega.privacy.android.domain.usecase.meeting.MonitorChatCallUpdates
import mega.privacy.android.domain.usecase.meeting.MonitorChatSessionUpdatesUseCase
import mega.privacy.android.domain.usecase.meeting.StartChatCall
import mega.privacy.android.domain.usecase.network.MonitorConnectivityUseCase
import mega.privacy.android.domain.usecase.setting.MonitorUpdatePushNotificationSettingsUseCase
import mega.privacy.android.domain.usecase.shares.GetInSharesUseCase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import test.mega.privacy.android.app.presentation.shares.FakeMonitorUpdates
import kotlin.random.Random

@ExperimentalCoroutinesApi
class ContactInfoViewModelTest {
    private lateinit var underTest: ContactInfoViewModel
    private lateinit var monitorStorageStateEventUseCase: MonitorStorageStateEventUseCase
    private lateinit var monitorConnectivityUseCase: MonitorConnectivityUseCase
    private lateinit var startChatCall: StartChatCall
    private lateinit var getChatRoomUseCase: GetChatRoomUseCase
    private lateinit var passcodeManagement: PasscodeManagement
    private lateinit var chatApiGateway: MegaChatApiGateway
    private lateinit var cameraGateway: CameraGateway
    private lateinit var chatManagement: ChatManagement
    private lateinit var monitorContactUpdates: MonitorContactUpdates
    private lateinit var getUserOnlineStatusByHandleUseCase: GetUserOnlineStatusByHandleUseCase
    private lateinit var requestLastGreen: RequestLastGreen
    private lateinit var getChatRoom: GetChatRoom
    private lateinit var getContactFromEmailUseCase: GetContactFromEmailUseCase
    private lateinit var getContactFromChatUseCase: GetContactFromChatUseCase
    private lateinit var getChatRoomByUserUseCase: GetChatRoomByUserUseCase
    private lateinit var applyContactUpdatesUseCase: ApplyContactUpdatesUseCase
    private lateinit var setUserAliasUseCase: SetUserAliasUseCase
    private lateinit var removeContactByEmailUseCase: RemoveContactByEmailUseCase
    private lateinit var getInSharesUseCase: GetInSharesUseCase
    private lateinit var monitorChatCallUpdates: MonitorChatCallUpdates
    private lateinit var monitorChatSessionUpdatesUseCase: MonitorChatSessionUpdatesUseCase
    private lateinit var monitorUpdatePushNotificationSettingsUseCase: MonitorUpdatePushNotificationSettingsUseCase
    private lateinit var startConversationUseCase: StartConversationUseCase
    private lateinit var createChatRoomUseCase: CreateChatRoomUseCase
    private val monitorNodeUpdates = FakeMonitorUpdates()
    private lateinit var createShareKey: CreateShareKey
    private lateinit var checkNameCollisionUseCase: CheckNameCollisionUseCase
    private lateinit var copyNodeUseCase: CopyNodeUseCase
    private lateinit var copyRequestMessageMapper: CopyRequestMessageMapper
    private val scheduler = TestCoroutineScheduler()
    private val standardDispatcher = StandardTestDispatcher(scheduler)
    private val testScope = CoroutineScope(UnconfinedTestDispatcher())
    private val testHandle = 123456L
    private val testEmail = "test@gmail.com"
    private val contactData = ContactData(
        alias = "Iron Man",
        avatarUri = "https://avatar.uri.com",
        fullName = "Tony Stark",
    )
    private val contactItem = ContactItem(
        handle = testHandle,
        email = "test@gmail.com",
        contactData = contactData,
        defaultAvatarColor = "red",
        visibility = UserVisibility.Visible,
        timestamp = 123456789,
        areCredentialsVerified = true,
        status = UserStatus.Online,
        lastSeen = 0,
    )
    private val chatRoom = ChatRoom(
        chatId = 123456L,
        changes = null,
        title = "Chat title",
    )

    private val connectivityFlow: MutableStateFlow<Boolean> = MutableStateFlow(true)

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        initMock()
        setDefaultMockValues()
        initViewModel()
    }

    private fun setDefaultMockValues() {
        whenever(monitorContactUpdates.invoke()).thenReturn(MutableSharedFlow())
    }

    private fun initViewModel() {
        underTest = ContactInfoViewModel(
            monitorStorageStateEventUseCase = monitorStorageStateEventUseCase,
            monitorConnectivityUseCase = monitorConnectivityUseCase,
            startChatCall = startChatCall,
            getChatRoomUseCase = getChatRoomUseCase,
            passcodeManagement = passcodeManagement,
            chatApiGateway = chatApiGateway,
            cameraGateway = cameraGateway,
            chatManagement = chatManagement,
            monitorContactUpdates = monitorContactUpdates,
            getUserOnlineStatusByHandleUseCase = getUserOnlineStatusByHandleUseCase,
            requestLastGreen = requestLastGreen,
            getChatRoom = getChatRoom,
            getChatRoomByUserUseCase = getChatRoomByUserUseCase,
            getContactFromChatUseCase = getContactFromChatUseCase,
            getContactFromEmailUseCase = getContactFromEmailUseCase,
            applyContactUpdatesUseCase = applyContactUpdatesUseCase,
            setUserAliasUseCase = setUserAliasUseCase,
            removeContactByEmailUseCase = removeContactByEmailUseCase,
            getInSharesUseCase = getInSharesUseCase,
            monitorChatCallUpdates = monitorChatCallUpdates,
            monitorChatSessionUpdatesUseCase = monitorChatSessionUpdatesUseCase,
            monitorUpdatePushNotificationSettingsUseCase = monitorUpdatePushNotificationSettingsUseCase,
            createChatRoomUseCase = createChatRoomUseCase,
            startConversationUseCase = startConversationUseCase,
            ioDispatcher = standardDispatcher,
            applicationScope = testScope,
            createShareKey = createShareKey,
            monitorNodeUpdates = monitorNodeUpdates,
            checkNameCollisionUseCase = checkNameCollisionUseCase,
            copyNodeUseCase = copyNodeUseCase,
            copyRequestMessageMapper = copyRequestMessageMapper,
        )
    }

    private fun initMock() {
        monitorStorageStateEventUseCase = mock()
        monitorConnectivityUseCase = mock()
        startChatCall = mock()
        getChatRoomUseCase = mock()
        passcodeManagement = mock()
        chatApiGateway = mock()
        cameraGateway = mock()
        chatManagement = mock()
        monitorContactUpdates = mock()
        getUserOnlineStatusByHandleUseCase = mock()
        requestLastGreen = mock()
        getChatRoom = mock()
        getChatRoomByUserUseCase = mock()
        getContactFromChatUseCase = mock()
        getContactFromEmailUseCase = mock()
        applyContactUpdatesUseCase = mock()
        setUserAliasUseCase = mock()
        removeContactByEmailUseCase = mock()
        getInSharesUseCase = mock()
        monitorChatCallUpdates = mock()
        monitorChatSessionUpdatesUseCase = mock()
        monitorUpdatePushNotificationSettingsUseCase = mock()
        startConversationUseCase = mock()
        createChatRoomUseCase = mock()
        createShareKey = mock()
        copyRequestMessageMapper = mock()
        copyNodeUseCase = mock()
        checkNameCollisionUseCase = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun initUserInfo() {
        whenever(getContactFromEmailUseCase(email = testEmail, skipCache = true)).thenReturn(
            contactItem
        )
        whenever(getChatRoomByUserUseCase(contactItem.handle)).thenReturn(chatRoom)
        whenever(monitorConnectivityUseCase()).thenReturn(connectivityFlow)
        underTest.updateContactInfo(chatHandle = -1L, email = testEmail)
    }

    @Test
    fun `test that initial user state is Invalid`() = runTest {
        underTest.state.test {
            val initialState = awaitItem()
            assertThat(initialState.userStatus).isEqualTo(UserStatus.Invalid)
            assertThat(initialState.lastGreen).isEqualTo(0)
            assertThat(initialState.areCredentialsVerified).isFalse()
            assertThat(initialState.isCallStarted).isFalse()
        }
    }

    @Test
    fun `test that get user status and request last green does not trigger last green when user status is online`() =
        runTest {
            initUserInfo()
            whenever(getUserOnlineStatusByHandleUseCase(testHandle)).thenReturn(UserStatus.Online)
            underTest.getUserStatusAndRequestForLastGreen()
            underTest.state.test {
                assertThat(awaitItem().userStatus).isEqualTo(UserStatus.Online)
                verifyNoInteractions(requestLastGreen)
            }
        }

    @Test
    fun `test that get user status and request last green triggers last green when user status is away`() =
        runTest {
            initUserInfo()
            whenever(getUserOnlineStatusByHandleUseCase(anyLong())).thenReturn(UserStatus.Away)
            underTest.getUserStatusAndRequestForLastGreen()
            underTest.state.test {
                assertThat(awaitItem().userStatus).isEqualTo(UserStatus.Away)
                verify(requestLastGreen).invoke(userHandle = anyLong())
            }
        }

    @Test
    fun `test when update last green method is called state is updated with the last green value`() =
        runTest {
            whenever(getUserOnlineStatusByHandleUseCase(testHandle)).thenReturn(UserStatus.Online)
            underTest.updateLastGreen(testHandle, lastGreen = 5)
            underTest.state.test {
                val nextState = awaitItem()
                assertThat(nextState.userStatus).isEqualTo(UserStatus.Online)
                assertThat(nextState.lastGreen).isEqualTo(5)
            }
        }

    @Test
    fun `test when contact info screen launched from contacts emits title`() =
        runTest {
            whenever(monitorConnectivityUseCase()).thenReturn(connectivityFlow)
            whenever(getChatRoom(testHandle)).thenReturn(chatRoom)
            whenever(
                getContactFromChatUseCase(
                    testHandle,
                    skipCache = true
                )
            ).thenReturn(contactItem)
            underTest.updateContactInfo(testHandle)
            underTest.state.test {
                val nextState = awaitItem()
                assertThat(nextState.primaryDisplayName).isEqualTo("Iron Man")
                assertThat(nextState.isFromContacts).isFalse()
                assertThat(nextState.email).isEqualTo("test@gmail.com")
            }
        }

    @Test
    fun `test when contact info screen launched from chats emits title`() =
        runTest {
            whenever(monitorConnectivityUseCase()).thenReturn(connectivityFlow)
            whenever(
                getContactFromEmailUseCase(
                    testEmail,
                    skipCache = true
                )
            ).thenReturn(contactItem)
            whenever(getChatRoomByUserUseCase(testHandle)).thenReturn(chatRoom)
            underTest.updateContactInfo(-1L, testEmail)
            underTest.state.test {
                val nextState = awaitItem()
                assertThat(nextState.primaryDisplayName).isEqualTo("Iron Man")
                assertThat(nextState.isFromContacts).isTrue()
                assertThat(nextState.email).isEqualTo("test@gmail.com")
            }
        }

    @Test
    fun `test when new nickname is given the nick name added snack bar message is emitted`() =
        runTest {
            whenever(monitorConnectivityUseCase()).thenReturn(connectivityFlow)
            whenever(
                getContactFromEmailUseCase(
                    testEmail,
                    skipCache = true
                )
            ).thenReturn(contactItem)
            whenever(getChatRoomByUserUseCase(testHandle)).thenReturn(chatRoom)
            whenever(setUserAliasUseCase("Spider Man", testHandle)).thenReturn("Spider Man")
            underTest.updateContactInfo(-1L, testEmail)
            verifyInitialData()
            underTest.updateNickName("Spider Man")
            underTest.state.test {
                val nextState = awaitItem()
                assertThat(nextState.snackBarMessage).isNotNull()
                assertThat(nextState.isFromContacts).isTrue()
                assertThat(nextState.email).isEqualTo("test@gmail.com")
            }
            underTest.onConsumeSnackBarMessageEvent()
            underTest.state.test {
                val nextState = awaitItem()
                assertThat(nextState.snackBarMessage).isNull()
            }
        }

    @Test
    fun `test that when remove contact is success isUserRemoved is emitted as true`() = runTest {
        initContactInfoOpenedFromContact()
        whenever(removeContactByEmailUseCase(testEmail)).thenReturn(true)
        verifyInitialData()
        underTest.removeContact()
        underTest.state.test {
            val nextState = awaitItem()
            assertThat(nextState.isUserRemoved).isTrue()
        }
    }

    private suspend fun initContactInfoOpenedFromContact() {
        whenever(monitorConnectivityUseCase()).thenReturn(connectivityFlow)
        whenever(getContactFromEmailUseCase(email = testEmail, skipCache = true))
            .thenReturn(contactItem)
        whenever(getChatRoomByUserUseCase(userHandle = testHandle)).thenReturn(chatRoom)
        underTest.updateContactInfo(chatHandle = -1L, email = testEmail)
    }

    @Test
    fun `test that if get in share is success the value is updated in state`() = runTest {
        val nodeList = mock<List<UnTypedNode>> {
            on { size }.thenReturn(5)
        }
        initContactInfoOpenedFromContact()
        whenever(getInSharesUseCase(testEmail)).thenReturn(nodeList)
        verifyInitialData()
        underTest.getInShares()
        underTest.state.test {
            val nextState = awaitItem()
            assertThat(nextState.inShares.size).isEqualTo(5)
        }
    }

    private suspend fun verifyInitialData() {
        underTest.state.test {
            val initialState = awaitItem()
            assertThat(initialState.primaryDisplayName).isEqualTo("Iron Man")
            assertThat(initialState.snackBarMessage).isNull()
        }
    }

    @Test
    fun `test that when chatNotificationsClicked is clicked chatNotificationChange is fired`() =
        runTest {
            initContactInfoOpenedFromContact()
            verifyInitialData()
            underTest.chatNotificationsClicked()
            underTest.state.test {
                val state = awaitItem()
                assertThat(state.isChatNotificationChange).isTrue()
            }
        }

    @Test
    fun `test that when chatNotificationsClicked is clicked new chat is created if chatroom does not exist`() =
        runTest {
            val newChatId = Random.nextLong()
            val newChatRoom = mock<ChatRoom> {
                on { chatId }.thenReturn(newChatId)
            }
            whenever(monitorConnectivityUseCase()).thenReturn(connectivityFlow)
            whenever(getContactFromEmailUseCase(email = testEmail, skipCache = true)).thenReturn(
                contactItem
            )
            whenever(getChatRoomByUserUseCase(userHandle = testHandle)).thenReturn(null)
            whenever(createChatRoomUseCase(isGroup = false, userHandles = listOf(testHandle)))
                .thenReturn(newChatId)
            whenever(getChatRoom(newChatId)).thenReturn(newChatRoom)
            underTest.updateContactInfo(chatHandle = -1L, email = testEmail)
            verifyInitialData()
            underTest.chatNotificationsClicked()
            underTest.state.test {
                val state = awaitItem()
                assertThat(state.isChatNotificationChange).isTrue()
                assertThat(state.chatRoom?.chatId).isEqualTo(newChatId)
            }
        }

    @Test
    fun `test that when send message is clicked chat activity is opened`() = runTest {
        initContactInfoOpenedFromContact()
        verifyInitialData()
        val chatId = Random.nextLong()
        val exampleStorageStateEvent = StorageStateEvent(
            handle = 1L,
            eventString = "eventString",
            number = 0L,
            text = "text",
            type = EventType.Storage,
            storageState = StorageState.Unknown
        )
        val storageFlow: MutableStateFlow<StorageStateEvent> =
            MutableStateFlow(exampleStorageStateEvent)
        whenever(monitorStorageStateEventUseCase()).thenReturn(storageFlow)
        whenever(startConversationUseCase(isGroup = false, userHandles = listOf(testHandle)))
            .thenReturn(chatId)
        whenever(getChatRoom(chatId)).thenReturn(chatRoom)
        underTest.sendMessageToChat()
        underTest.state.test {
            val state = awaitItem()
            assertThat(state.chatRoom?.chatId).isEqualTo(123456L)
            assertThat(state.shouldNavigateToChat).isTrue()
        }
    }

    @Test
    fun `test that when node update is triggered state is updated`() = runTest {
        val unTypedNodeNew = mock<FolderNode> {
            on { parentId.longValue }.thenReturn(-1L)
        }
        val node = mock<Node> {
            on { isIncomingShare }.thenReturn(true)
        }
        val nodeUpdate = mock<NodeUpdate> {
            on { changes }.thenReturn(mapOf(Pair(node, listOf(NodeChanges.Remove))))
        }
        initContactInfoOpenedFromContact()
        underTest.state.test {
            val initialState = awaitItem()
            assertThat(initialState.primaryDisplayName).isEqualTo("Iron Man")
            assertThat(initialState.snackBarMessage).isNull()
            whenever(getInSharesUseCase(any())).thenReturn(listOf(unTypedNodeNew))
            monitorNodeUpdates.emit(nodeUpdate)
            val newState = awaitItem()
            assertThat(newState.isNodeUpdated).isTrue()
        }
    }

    @Test
    fun `test that when onConsumeIsTransferComplete is triggered state is updated`() = runTest {
        initContactInfoOpenedFromContact()
        verifyInitialData()
        underTest.onConsumeIsTransferComplete()
        underTest.state.test {
            val state = awaitItem()
            assertThat(state.isTransferComplete).isFalse()
        }
    }

    @Test
    fun `test that when onConsumeNameCollisions is triggered state is updated`() = runTest {
        initContactInfoOpenedFromContact()
        verifyInitialData()
        underTest.onConsumeNameCollisions()
        underTest.state.test {
            val state = awaitItem()
            assertThat(state.nameCollisions).isEmpty()
        }
    }

    @Test
    fun `test that when onConsumeCopyException is called state is updated`() = runTest {
        initContactInfoOpenedFromContact()
        verifyInitialData()
        underTest.onConsumeCopyException()
        underTest.state.test {
            val state = awaitItem()
            assertThat(state.copyError).isNull()
        }
    }

    @Test
    fun `test that when onConsumeNodeUpdateEvent is called state is updated`() = runTest {
        initContactInfoOpenedFromContact()
        verifyInitialData()
        underTest.onConsumeNodeUpdateEvent()
        underTest.state.test {
            val state = awaitItem()
            assertThat(state.isNodeUpdated).isFalse()
        }
    }

    @Test
    fun `test that when onConsumeStorageOverQuotaEvent is called state is updated`() = runTest {
        initContactInfoOpenedFromContact()
        verifyInitialData()
        underTest.onConsumeStorageOverQuotaEvent()
        underTest.state.test {
            val state = awaitItem()
            assertThat(state.isStorageOverQuota).isFalse()
        }
    }

    @Test
    fun `test that when onConsumeChatNotificationChangeEvent is called state is updated`() =
        runTest {
            initContactInfoOpenedFromContact()
            verifyInitialData()
            underTest.onConsumeChatNotificationChangeEvent()
            underTest.state.test {
                val state = awaitItem()
                assertThat(state.isChatNotificationChange).isFalse()
            }
        }

    @Test
    fun `test that when onConsumeNavigateToChatEvent is called state is updated`() = runTest {
        initContactInfoOpenedFromContact()
        verifyInitialData()
        underTest.onConsumeNavigateToChatEvent()
        underTest.state.test {
            val state = awaitItem()
            assertThat(state.shouldNavigateToChat).isFalse()
        }
    }

    @Test
    fun `test that when onConsumePushNotificationSettingsUpdateEvent is called state is updated`() =
        runTest {
            initContactInfoOpenedFromContact()
            verifyInitialData()
            underTest.onConsumePushNotificationSettingsUpdateEvent()
            underTest.state.test {
                val state = awaitItem()
                assertThat(state.isPushNotificationSettingsUpdated).isFalse()
            }
        }

    @Test
    fun `test that when onConsumeSnackBarMessageEvent is called state is updated`() = runTest {
        initContactInfoOpenedFromContact()
        verifyInitialData()
        underTest.onConsumeSnackBarMessageEvent()
        underTest.state.test {
            val state = awaitItem()
            assertThat(state.snackBarMessage).isNull()
            assertThat(state.snackBarMessageString).isNull()
        }
    }

    @Test
    fun `test that when onConsumeChatCallStatusChangeEvent is called state is updated`() = runTest {
        initContactInfoOpenedFromContact()
        verifyInitialData()
        underTest.onConsumeChatCallStatusChangeEvent()
        underTest.state.test {
            val state = awaitItem()
            assertThat(state.callStatusChanged).isFalse()
        }
    }
}
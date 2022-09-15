package mega.privacy.android.app.presentation.startconversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mega.privacy.android.app.R
import mega.privacy.android.app.data.extensions.findItemByHandle
import mega.privacy.android.app.data.extensions.replaceIfExists
import mega.privacy.android.app.data.extensions.sortList
import mega.privacy.android.app.di.IoDispatcher
import mega.privacy.android.app.presentation.extensions.getStateFlow
import mega.privacy.android.app.presentation.startconversation.model.StartConversationState
import mega.privacy.android.domain.entity.contacts.ContactItem
import mega.privacy.android.domain.entity.contacts.UserStatus
import mega.privacy.android.domain.usecase.AddNewContacts
import mega.privacy.android.domain.usecase.ApplyContactUpdates
import mega.privacy.android.domain.usecase.GetContactData
import mega.privacy.android.domain.usecase.GetVisibleContacts
import mega.privacy.android.domain.usecase.MonitorConnectivity
import mega.privacy.android.domain.usecase.MonitorContactRequestUpdates
import mega.privacy.android.domain.usecase.MonitorContactUpdates
import mega.privacy.android.domain.usecase.MonitorLastGreenUpdates
import mega.privacy.android.domain.usecase.MonitorOnlineStatusUpdates
import mega.privacy.android.domain.usecase.RequestLastGreen
import mega.privacy.android.domain.usecase.StartConversation
import mega.privacy.android.presentation.controls.SearchWidgetState
import timber.log.Timber
import javax.inject.Inject

/**
 * StartConversationFragment view model.
 *
 * @property getVisibleContacts           [GetVisibleContacts]
 * @property getContactData               [GetContactData]
 * @property startConversation            [StartConversation]
 * @property monitorContactUpdates        [MonitorContactUpdates]
 * @property applyContactUpdates          [ApplyContactUpdates]
 * @property monitorLastGreenUpdates      [MonitorLastGreenUpdates]
 * @property monitorOnlineStatusUpdates   [MonitorOnlineStatusUpdates]
 * @property monitorContactRequestUpdates [MonitorContactRequestUpdates]
 * @property addNewContacts               [AddNewContacts]
 * @property requestLastGreen             [RequestLastGreen]
 * @property ioDispatcher                 [CoroutineDispatcher]
 * @property state                        Current view state as [StartConversationState]
 */
@HiltViewModel
class StartConversationViewModel @Inject constructor(
    private val getVisibleContacts: GetVisibleContacts,
    private val getContactData: GetContactData,
    private val startConversation: StartConversation,
    private val monitorContactUpdates: MonitorContactUpdates,
    private val applyContactUpdates: ApplyContactUpdates,
    private val monitorLastGreenUpdates: MonitorLastGreenUpdates,
    private val monitorOnlineStatusUpdates: MonitorOnlineStatusUpdates,
    private val monitorContactRequestUpdates: MonitorContactRequestUpdates,
    private val addNewContacts: AddNewContacts,
    private val requestLastGreen: RequestLastGreen,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    monitorConnectivity: MonitorConnectivity,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(StartConversationState())
    val state: StateFlow<StartConversationState> = _state

    private val isConnected =
        monitorConnectivity().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    internal val contactListKey = "CONTACT_LIST"
    internal val searchExpandedKey = "SEARCH_EXPANDED"
    internal val typedSearchKey = "TYPED_SEARCH"
    internal val fromChatKey = "FROM_CHAT"

    private val contactList = savedStateHandle.getStateFlow(
        viewModelScope,
        contactListKey,
        listOf<ContactItem>()
    )

    private val searchExpanded = savedStateHandle.getStateFlow(
        viewModelScope,
        searchExpandedKey,
        SearchWidgetState.COLLAPSED
    )

    private val typedSearch = savedStateHandle.getStateFlow(
        viewModelScope,
        typedSearchKey,
        ""
    )

    private val fromChat = savedStateHandle.getStateFlow(
        viewModelScope,
        fromChatKey,
        false
    )

    init {
        observeStateChanges()
        observeStateChanges()
        getContacts()
        observeContactUpdates()
        observeLastGreenUpdates()
        observeOnlineStatusUpdates()
        observeNewContacts()
    }

    /**
     * Sets from chat state value.
     */
    fun setFromChat(fromChat: Boolean) {
        this.fromChat.update { fromChat }
    }

    private fun observeStateChanges() {
        viewModelScope.launch(ioDispatcher) {
            merge(
                contactList.map { list ->
                    { state: StartConversationState ->
                        state.copy(contactItemList = list,
                            emptyViewVisible = list.isEmpty(),
                            searchAvailable = list.isNotEmpty(),
                            filteredContactList = getFilteredContactList(
                                contactList = list,
                                typedSearch = typedSearch.value))
                    }
                },
                searchExpanded.map { widgetState ->
                    { state: StartConversationState ->
                        state.copy(searchWidgetState = widgetState,
                            buttonsVisible = widgetState != SearchWidgetState.EXPANDED)
                    }
                },
                typedSearch.map { typed ->
                    { state: StartConversationState ->
                        state.copy(typedSearch = typed,
                            filteredContactList = getFilteredContactList(
                                contactList = contactList.value,
                                typedSearch = typed))
                    }
                },
                fromChat.map { isFromChat ->
                    { state: StartConversationState -> state.copy(fromChat = isFromChat) }
                }
            ).collect {
                _state.update(it)
            }
        }
    }

    private fun getFilteredContactList(
        contactList: List<ContactItem>,
        typedSearch: String,
    ): List<ContactItem>? =
        if (typedSearch.isEmpty()) null
        else contactList.filter { (_, email, fullName, alias) ->
            val filter = typedSearch.lowercase()

            email.lowercase().contains(filter)
                    || fullName?.lowercase()?.contains(filter) == true
                    || alias?.lowercase()?.contains(filter) == true
        }

    private fun getContacts() {
        viewModelScope.launch(ioDispatcher) {
            contactList.update { getVisibleContacts() }
            getContactsData()
        }
    }

    private suspend fun getContactsData() {
        contactList.value.forEach { contactItem ->
            withContext(ioDispatcher) {
                val contactData = getContactData(contactItem)
                contactList.value.findItemByHandle(contactItem.handle)?.apply {
                    contactList.value.toMutableList().apply {
                        replaceIfExists(copy(
                            fullName = contactData.fullName,
                            alias = contactData.alias,
                            avatarUri = contactData.avatarUri
                        ))
                        contactList.update { this.sortList() }
                    }
                }
            }
        }
    }

    private fun observeContactUpdates() {
        viewModelScope.launch(ioDispatcher) {
            monitorContactUpdates().collectLatest { userUpdates ->
                contactList.update { applyContactUpdates(contactList.value, userUpdates) }
            }
        }
    }

    private fun observeLastGreenUpdates() {
        viewModelScope.launch(ioDispatcher) {
            monitorLastGreenUpdates().collectLatest { (handle, lastGreen) ->
                contactList.value.findItemByHandle(handle)?.apply {
                    contactList.value.toMutableList().apply {
                        replaceIfExists(copy(lastSeen = lastGreen))
                        contactList.update { this.sortList() }
                    }
                }
            }
        }
    }

    private fun observeOnlineStatusUpdates() {
        viewModelScope.launch(ioDispatcher) {
            monitorOnlineStatusUpdates().collectLatest { (userHandle, status) ->
                if (status != UserStatus.Online) {
                    requestLastGreen(userHandle)
                }

                contactList.value.findItemByHandle(userHandle)?.apply {
                    contactList.value.toMutableList().apply {
                        replaceIfExists(copy(status = status))
                        contactList.update { this.sortList() }
                    }
                }
            }
        }
    }

    private fun observeNewContacts() {
        viewModelScope.launch(ioDispatcher) {
            monitorContactRequestUpdates().collectLatest { newContacts ->
                contactList.update { addNewContacts(contactList.value, newContacts) }
            }
        }
    }

    /**
     * Sets the search expanded value.
     *
     * @param widgetState [SearchWidgetState].
     */
    fun updateSearchWidgetState(widgetState: SearchWidgetState) {
        searchExpanded.update { widgetState }
    }

    /**
     * Sets the typed search.
     *
     * @param newTypedText  New typed search.
     */
    fun setTypedSearch(newTypedText: String) {
        typedSearch.update { newTypedText }
    }

    /**
     * Starts a conversation if there is internet connection, shows an error if not.
     */
    fun onContactTap(contactItem: ContactItem) {
        if (isConnected.value) {
            viewModelScope.launch(ioDispatcher) {
                runCatching {
                    startConversation(false, listOf(contactItem.handle))
                }.onFailure { exception ->
                    Timber.e(exception)
                    _state.update { it.copy(result = -1L, error = R.string.general_text_error) }
                }.onSuccess { chatHandle -> _state.update { it.copy(result = chatHandle) } }
            }
        } else {
            _state.update { it.copy(error = R.string.check_internet_connection_error) }
        }
    }

    /**
     * Updates searchWidgetState as [SearchWidgetState.COLLAPSED]
     */
    fun onCloseSearchTap() {
        updateSearchWidgetState(SearchWidgetState.COLLAPSED)
        setTypedSearch("")
    }

    /**
     * Updates searchWidgetState as [SearchWidgetState.EXPANDED]
     */
    fun onSearchTap() {
        updateSearchWidgetState(SearchWidgetState.EXPANDED)
    }
}
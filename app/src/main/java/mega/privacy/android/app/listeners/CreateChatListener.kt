package mega.privacy.android.app.listeners

import android.content.Context
import mega.privacy.android.app.R
import mega.privacy.android.app.interfaces.SnackbarShower
import mega.privacy.android.app.interfaces.showSnackbar
import mega.privacy.android.app.main.listeners.MultipleForwardChatProcessor
import nz.mega.sdk.MegaChatApiJava
import nz.mega.sdk.MegaChatError
import nz.mega.sdk.MegaChatRequest
import nz.mega.sdk.MegaChatRequest.TYPE_CREATE_CHATROOM
import nz.mega.sdk.MegaChatRoom
import nz.mega.sdk.MegaError.API_OK
import nz.mega.sdk.MegaUser

/**
 * Create chat listener
 *
 * @property action
 * @property totalCount
 * @property snackbarShower
 * @property onChatsCreated
 * @property callback
 * @constructor
 *
 * @param context
 */
class CreateChatListener(
    context: Context,
    private val action: Int = ATTACH,
    private val totalCount: Int,
    private val snackbarShower: SnackbarShower? = null,
    private val onChatsCreated: ((List<MegaChatRoom>) -> Unit)? = null,
    private val callback: ((List<Long>, Int) -> Unit)?,
) : ChatBaseListener(context) {

    private var successChats = ArrayList<Long>()
    private var failureCount = 0

    private var chats = ArrayList<MegaChatRoom>()

    // If the given users with no chat room is MEGAUsers
    private val megaUsersNoChat = ArrayList<MegaUser>()

    // If the given users with no chat room is Users
    private var usersNoChatSize = 0

    private var counter = 0
    private var error = 0

    private var messageHandles: LongArray? = null
    private var chatId = MegaChatApiJava.MEGACHAT_INVALID_HANDLE

    constructor(
        action: Int,
        chats: List<MegaChatRoom>,
        usersNoChatSize: Int,
        context: Context,
        snackbarShower: SnackbarShower,
        onChatsCreated: (List<MegaChatRoom>) -> Unit,
    ) : this(context, action, usersNoChatSize + chats.size, snackbarShower, onChatsCreated, null) {
        this.chats.addAll(chats)
        this.usersNoChatSize = usersNoChatSize

        counter = usersNoChatSize
    }

    constructor(
        action: Int,
        chats: List<MegaChatRoom>,
        usersNoChat: List<MegaUser>,
        context: Context,
        snackbarShower: SnackbarShower,
        messageHandles: LongArray,
        chatId: Long,
    ) : this(context, action, usersNoChat.size + chats.size, snackbarShower, null, null) {
        initFields(chats, usersNoChat)

        this.messageHandles = messageHandles
        this.chatId = chatId
    }

    private fun initFields(
        chats: List<MegaChatRoom>,
        megaUsersNoChat: List<MegaUser>,
    ) {
        this.chats.addAll(chats)
        this.megaUsersNoChat.addAll(megaUsersNoChat)

        counter = megaUsersNoChat.size
    }

    override fun onRequestFinish(api: MegaChatApiJava, request: MegaChatRequest, e: MegaChatError) {
        if (request.type != TYPE_CREATE_CHATROOM) {
            return
        }

        if (action == ATTACH) {
            if (e.errorCode == API_OK) {
                successChats.add(request.chatHandle)
            } else {
                failureCount++
            }

            if (successChats.size + failureCount == totalCount) {
                callback?.invoke(successChats, failureCount)
            }

            return
        }

        counter--

        if (e.errorCode != API_OK) {
            error++
        } else {
            val chat = api.getChatRoom(request.chatHandle)

            if (chat != null) {
                chats.add(chat)
            }
        }

        if (counter > 0) {
            return
        }

        when (action) {
            SEND_MESSAGES -> {
                val handles = messageHandles ?: return

                if (errorCreatingChat()) {
                    // All send messages fail; Show error
                    snackbarShower?.showSnackbar(
                        context.resources.getQuantityString(
                            R.plurals.num_messages_not_send,
                            handles.size,
                            totalCount
                        )
                    )
                } else {
                    // Send messages
                    val chatHandles = LongArray(chats.size)

                    for (i in chats.indices) {
                        chatHandles[i] = chats[i].chatId
                    }

                    val forwardChatProcessor =
                        MultipleForwardChatProcessor(context, chatHandles, handles, chatId)
                    forwardChatProcessor.forward(api.getChatRoom(chatId))
                }
            }

            SEND_FILE_EXPLORER_CONTENT -> {
                if (usersNoChatSize == error && chats.isEmpty()) {
                    // All send messages fail; Show error
                    snackbarShower?.showSnackbar(
                        context.getString(
                            R.string.content_not_send,
                            totalCount
                        )
                    )
                } else {
                    // Send content
                    onChatsCreated?.invoke(chats)
                }
            }
        }
    }

    /**
     * Method to check if there has been error in creating the chat/chats.
     *
     * @return True, if there has been an error. False, otherwise.
     */
    private fun errorCreatingChat(): Boolean {
        return megaUsersNoChat.size == error && chats.isEmpty()
    }

    companion object {
        const val ATTACH = 1
        const val SEND_MESSAGES = 6
        const val SEND_FILE_EXPLORER_CONTENT = 7
    }
}

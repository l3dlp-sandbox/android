package mega.privacy.android.app.meeting.fragments

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_meeting.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mega.privacy.android.app.R
import mega.privacy.android.app.databinding.InMeetingFragmentBinding
import mega.privacy.android.app.interfaces.SnackbarShower
import mega.privacy.android.app.lollipop.AddContactActivityLollipop
import mega.privacy.android.app.lollipop.megachat.AppRTCAudioManager
import mega.privacy.android.app.lollipop.megachat.calls.OnDragTouchListener
import mega.privacy.android.app.meeting.AnimationTool.fadeInOut
import mega.privacy.android.app.meeting.AnimationTool.moveY
import mega.privacy.android.app.meeting.BottomFloatingPanelListener
import mega.privacy.android.app.meeting.BottomFloatingPanelViewHolder
import mega.privacy.android.app.meeting.TestTool
import mega.privacy.android.app.meeting.activity.LeftMeetingActivity
import mega.privacy.android.app.meeting.activity.MeetingActivity.Companion.MEETING_CHAT_ID
import mega.privacy.android.app.meeting.adapter.Participant
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.app.utils.LogUtil.logDebug
import mega.privacy.android.app.utils.StringResourcesUtils
import nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE

@AndroidEntryPoint
class InMeetingFragment : MeetingBaseFragment(), BottomFloatingPanelListener, SnackbarShower {

    // Views
    lateinit var toolbar: MaterialToolbar
    private lateinit var floatingWindowContainer: View
    private lateinit var floatingBottomSheet: View

    lateinit var bottomFloatingPanelViewHolder: BottomFloatingPanelViewHolder

    private lateinit var gridViewMenuItem: MenuItem
    private lateinit var speakerViewMenuItem: MenuItem

    // Children fragments
    private lateinit var individualCallFragment: IndividualCallFragment
    private lateinit var floatingWindowFragment: IndividualCallFragment
    private lateinit var gridViewCallFragment: GridViewCallFragment
    private lateinit var speakerViewCallFragment: SpeakerViewCallFragment

    private var chatId: Long = MEGACHAT_INVALID_HANDLE

    // Flags, should get the value from somewhere
    private var isGuest = false
    private var isModerator = true
    private var isOneToOneChat = false

    // For internal UI/UX use
    private var previousY = -1f
    private var lastTouch: Long = 0
    private lateinit var dragTouchListener: OnDragTouchListener

    private lateinit var binding: InMeetingFragmentBinding

    val inMeetingViewModel by viewModels<InMeetingViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chatId = arguments?.getLong(MEETING_CHAT_ID)!!
        if (chatId != MEGACHAT_INVALID_HANDLE) {
            val chatRoom = megaChatApi.getChatRoom(chatId)
            if (chatRoom != null) {
                isOneToOneChat = !chatRoom.isGroup
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = InMeetingFragmentBinding.inflate(inflater)

        floatingWindowContainer = binding.selfFeedFloatingWindowContainer
        floatingBottomSheet = binding.bottomFloatingPanel.root

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO test code start: add x participants
        val x = 2
        for (i in 0 until x) {
            inMeetingViewModel.addParticipant(true)
        }
        // TODO test code start

        //TODO test code start
        val producer = TestTool.FrameProducer()

        GlobalScope.launch(Dispatchers.IO) {
            producer.load()
            inMeetingViewModel.frames.postValue(producer.frames)
        }
        //TODO test code end

        initToolbar()
        initFloatingWindowContainerDragListener(view)
        initChildrenFragments()
        initFloatingPanel()
        initShareViewModel()

        // Set on page tapping listener.
        view.setOnClickListener {
            onPageClick()
        }

        // Set system UI color to make them visible.
        // decor.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        meetingActivity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 0x00000010
    }

    override fun onDestroy() {
        super.onDestroy()
        //TODO test code start
        inMeetingViewModel.frames.value = mutableListOf()
        //TODO test code end
    }

    fun onPageClick() {
        // Prevent fast tapping.
        if (System.currentTimeMillis() - lastTouch < TAP_THRESHOLD) return

        toolbar.fadeInOut(dy = TOOLBAR_DY, toTop = true)
        floatingBottomSheet.fadeInOut(dy = FLOATING_BOTTOM_SHEET_DY, toTop = false)

        if (toolbar.isVisible) {
            meetingActivity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            meetingActivity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        checkRelativePositionWithToolbar()
        checkRelativePositionWithBottomSheet()

        lastTouch = System.currentTimeMillis()
    }

    private fun checkRelativePositionWithToolbar() {
        val isIntersect = (toolbar.bottom - floatingWindowContainer.y) > 0
        if (toolbar.isVisible && isIntersect) {
            floatingWindowContainer.moveY(toolbar.bottom.toFloat())
        }

        val isIntersectPreviously = (toolbar.bottom - previousY) > 0
        if (!toolbar.isVisible && isIntersectPreviously && previousY >= 0) {
            floatingWindowContainer.moveY(previousY)
        }
    }

    private fun checkRelativePositionWithBottomSheet() {
        val bottom = floatingWindowContainer.y + floatingWindowContainer.height
        val top = floatingBottomSheet.top
        val margin1 = bottom - top

        val isIntersect = margin1 > 0
        if (floatingBottomSheet.isVisible && isIntersect) {
            floatingWindowContainer.moveY(floatingWindowContainer.y - margin1)
        }

        val margin2 = previousY + floatingWindowContainer.height - floatingBottomSheet.top
        val isIntersectPreviously = margin2 > 0
        if (!floatingBottomSheet.isVisible && isIntersectPreviously && previousY >= 0) {
            floatingWindowContainer.moveY(previousY)
        }
    }

    private fun initChildrenFragments() {
        //TODO test code start
        individualCallFragment = IndividualCallFragment.newInstance(1, 2, false)
        gridViewCallFragment = GridViewCallFragment.newInstance()
        speakerViewCallFragment = SpeakerViewCallFragment.newInstance()

        loadChildFragment(
            R.id.meeting_container,
            gridViewCallFragment,
            GridViewCallFragment.TAG
        )

        floatingWindowFragment = IndividualCallFragment.newInstance(1, 2, true)
        loadChildFragment(
            R.id.self_feed_floating_window_container,
            floatingWindowFragment,
            IndividualCallFragment.TAG
        )
        //TODO test code end
    }

    private fun initFloatingWindowContainerDragListener(view: View) {
        dragTouchListener = OnDragTouchListener(
            floatingWindowContainer,
            view,
            object : OnDragTouchListener.OnDragActionListener {

                override fun onDragStart(view: View?) {
                    if (toolbar.isVisible) {
                        dragTouchListener.setToolbarHeight(toolbar.bottom)
                        dragTouchListener.setBottomSheetHeight(floatingBottomSheet.top)
                    } else {
                        dragTouchListener.setToolbarHeight(0)
                        dragTouchListener.setBottomSheetHeight(0)
                    }
                }

                override fun onDragEnd(view: View) {
                    // Record the last Y of the floating window after dragging ended.
                    previousY = view.y
                }

            }
        )

        floatingWindowContainer.setOnTouchListener(dragTouchListener)
    }

    private fun initToolbar() {
        toolbar = meetingActivity.toolbar
        // TODO test code start
        toolbar.title = "Joanna's meeting"
        toolbar.subtitle = "Calling.."
        // TODO test code end

        meetingActivity.setSupportActionBar(toolbar)
        val actionBar = meetingActivity.supportActionBar ?: return
        actionBar.setHomeButtonEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        setHasOptionsMenu(true)
    }

    private fun loadChildFragment(containerId: Int, fragment: Fragment, tag: String) {
        childFragmentManager.beginTransaction().replace(
            containerId,
            fragment,
            tag
        ).commit()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.in_meeting_fragment_menu, menu)
        speakerViewMenuItem = menu.findItem(R.id.speaker_view)
        gridViewMenuItem = menu.findItem(R.id.grid_view)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.swap_camera -> {
                //TODO test code start: add or remove last participants
                inMeetingViewModel.addParticipant(true)
//                logDebug("Swap camera.")
//                VideoCaptureUtils.swapCamera(ChatChangeVideoStreamListener(requireContext()))
                //TODO test code end: add or remove last participants
                true
            }
            R.id.grid_view -> {
                logDebug("Change to grid view.")
                gridViewMenuItem.isVisible = false
                speakerViewMenuItem.isVisible = true

                loadChildFragment(
                    R.id.meeting_container,
                    gridViewCallFragment,
                    GridViewCallFragment.TAG
                )
                true
            }
            R.id.speaker_view -> {
                logDebug("Change to speaker view.")
                gridViewMenuItem.isVisible = true
                speakerViewMenuItem.isVisible = false

                loadChildFragment(
                    R.id.meeting_container,
                    speakerViewCallFragment,
                    SpeakerViewCallFragment.TAG
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Init Share View Model
     */
    private fun initShareViewModel() {
        sharedModel.micLiveData.observe(viewLifecycleOwner) {
            updateAudio(it)
        }
        sharedModel.cameraLiveData.observe(viewLifecycleOwner) {
            updateVideo(it)
        }

        sharedModel.speakerLiveData.observe(viewLifecycleOwner) {
            updateSpeaker(it)
        }

        /**
         * Will Change after Andy modify the permission structure
         */
        sharedModel.cameraPermissionCheck.observe(viewLifecycleOwner) {
            if (it) {
                checkMeetingPermissions(
                    arrayOf(Manifest.permission.CAMERA),
                ) { showRequestPermissionSnackBar() }
            }
        }
        sharedModel.recordAudioPermissionCheck.observe(viewLifecycleOwner) {
            if (it) {
                checkMeetingPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                ) { showRequestPermissionSnackBar() }
            }
        }
    }

    override fun showSnackbar(type: Int, content: String?, chatId: Long) {
        meetingActivity.showSnackbar(type, binding.root, content, chatId)
    }

    private fun showRequestPermissionSnackBar() {
        val warningText =
            StringResourcesUtils.getString(R.string.meeting_required_permissions_warning)
        showSnackbar(Constants.PERMISSIONS_TYPE, warningText, MEGACHAT_INVALID_HANDLE)
    }

    /**
     * Init Floating Panel, will move to `inMeetingFragment` later
     */
    private fun initFloatingPanel() {
        bottomFloatingPanelViewHolder =
            BottomFloatingPanelViewHolder(binding, this, isGuest, isModerator)
        bottomFloatingPanelViewHolder.collapse()

        /**
         * Observer the participant List
         */
        inMeetingViewModel.participants.observe(viewLifecycleOwner) { participants ->
            participants?.let {
                bottomFloatingPanelViewHolder.setParticipants(it)
            }
        }

        bottomFloatingPanelViewHolder.propertyUpdaters.add {
            toolbar.alpha = 1 - it
        }
    }

    /**
     * Change Mic State
     */
    override fun onChangeMicState(micOn: Boolean) {
        sharedModel.clickMic(!micOn)
    }

    private fun updateAudio(micOn: Boolean) {
        bottomFloatingPanelViewHolder.updateMicIcon(micOn)
    }

    /**
     * Change Cam State
     */
    override fun onChangeCamState(camOn: Boolean) {
        sharedModel.clickCamera(!camOn)
    }

    private fun updateVideo(camOn: Boolean) {
        bottomFloatingPanelViewHolder.updateCamIcon(camOn)
    }

    /**
     * Change Hold State
     */
    override fun onChangeHoldState(isHold: Boolean) {
        inMeetingViewModel.setCallOnHold(chatId, isHold)
    }

    /**
     * Change Speaker state
     */
    override fun onChangeSpeakerState() {
        sharedModel.clickSpeaker()
    }

    private fun updateSpeaker(device: AppRTCAudioManager.AudioDevice) {
        bottomFloatingPanelViewHolder.updateSpeakerIcon(device)
    }

    /**
     * Pop up dialog for end meeting for the user/guest
     *
     * Will show bottom sheet fragment for the moderator
     */
    override fun onEndMeeting() {
        when {
            isOneToOneChat -> {
                leaveMeeting()
            }
            isModerator -> {
                val endMeetingBottomSheetDialogFragment =
                    EndMeetingBottomSheetDialogFragment.newInstance()
                endMeetingBottomSheetDialogFragment.show(
                    parentFragmentManager,
                    endMeetingBottomSheetDialogFragment.tag
                )
            }
            else -> {
                askConfirmationEndMeetingForUser()
            }
        }
    }

    /**
     * Dialog for confirming leave meeting action
     */
    private fun askConfirmationEndMeetingForUser() {
        MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_Mega_MaterialAlertDialog
        ).apply {
            setMessage(getString(R.string.title_end_meeting))
            setPositiveButton(R.string.general_ok) { _, _ -> leaveMeeting() }
            setNegativeButton(R.string.general_cancel, null)
            show()
        }
    }

    private fun leaveMeeting() {
        if (isGuest && !isOneToOneChat) {
            meetingActivity.startActivity(Intent(meetingActivity, LeftMeetingActivity::class.java))
            meetingActivity.finish()
        } else {
            inMeetingViewModel.leaveMeeting(chatId)
            meetingActivity.finish()
        }
    }

    /**
     * Send share link
     */
    override fun onShareLink() {
        showShortToast("onShareLink")

        startActivity(Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, getShareLink())
            type = "text/plain"
        })
    }

    /**
     * Get the special link
     */
    private fun getShareLink(): String {
        return "This is the share link"
    }

    /**
     * Open invite participant page
     */
    override fun onInviteParticipants() {
        logDebug("chooseAddContactDialog")

        val inviteParticipantIntent =
            Intent(meetingActivity, AddContactActivityLollipop::class.java).apply {
                putExtra("contactType", Constants.CONTACT_TYPE_MEGA)
                putExtra("chat", true)
                putExtra("chatId", 123L)
                putExtra("aBtitle", getString(R.string.invite_participants))
            }
        meetingActivity.startActivityForResult(
            inviteParticipantIntent, Constants.REQUEST_ADD_PARTICIPANTS
        )
    }

    /**
     * Show participant bottom sheet when user click the three dots on participant item
     */
    override fun onParticipantOption(participant: Participant) {
        val participantBottomSheet =
            MeetingParticipantBottomSheetDialogFragment.newInstance(
                isGuest,
                isModerator,
                participant
            )
        participantBottomSheet.show(parentFragmentManager, participantBottomSheet.tag)
    }

    private fun showShortToast(message: String) {
        Toast.makeText(meetingActivity, message, Toast.LENGTH_SHORT).show()
    }

    companion object {

        const val ANIMATION_DURATION: Long = 500

        const val TAP_THRESHOLD: Long = 500

        const val TOOLBAR_DY = 300f

        const val FLOATING_BOTTOM_SHEET_DY = 400f
    }
}
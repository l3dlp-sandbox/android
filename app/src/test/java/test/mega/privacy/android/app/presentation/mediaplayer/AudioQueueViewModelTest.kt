package test.mega.privacy.android.app.presentation.mediaplayer

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mega.privacy.android.app.mediaplayer.mapper.MediaQueueItemUiEntityMapper
import mega.privacy.android.app.mediaplayer.playlist.PlaylistItem
import mega.privacy.android.app.mediaplayer.queue.audio.AudioQueueViewModel
import mega.privacy.android.app.mediaplayer.queue.model.MediaQueueItemType
import mega.privacy.android.app.mediaplayer.queue.model.MediaQueueItemUiEntity
import mega.privacy.android.app.presentation.time.mapper.DurationInSecondsTextMapper
import mega.privacy.android.domain.entity.node.NodeId
import mega.privacy.android.domain.usecase.meeting.IsParticipatingInChatCallUseCase
import mega.privacy.android.icon.pack.R
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import kotlin.time.Duration.Companion.minutes

class AudioQueueViewModelTest {
    private lateinit var underTest: AudioQueueViewModel

    private val mediaQueueItemUiEntityMapper = mock<MediaQueueItemUiEntityMapper>()
    private val durationInSecondsTextMapper = mock<DurationInSecondsTextMapper>()
    private val isParticipatingInChatCallUseCase = mock<IsParticipatingInChatCallUseCase>()

    private val testIcon = R.drawable.ic_audio_medium_solid
    private val testNodeId = NodeId(1L)
    private val testName = "Audio"
    private val testType = MediaQueueItemType.Playing
    private val testDuration = 10.minutes

    private val testPlaylistItem = mock<PlaylistItem> {
        on { nodeHandle }.thenReturn(1L)
        on { thumbnail }.thenReturn(null)
        on { nodeName }.thenReturn(testName)
        on { type }.thenReturn(2)
        on { duration }.thenReturn(testDuration)
    }

    private fun getPlaylistItem(handle: Long) = mock<PlaylistItem> {
        on { nodeHandle }.thenReturn(handle)
        on { thumbnail }.thenReturn(null)
        on { nodeName }.thenReturn(testName)
        on { type }.thenReturn(2)
        on { duration }.thenReturn(testDuration)
    }

    @BeforeEach
    fun setUp() {
        wheneverBlocking { isParticipatingInChatCallUseCase() }.thenReturn(false)
        initUnderTest()
    }

    private fun initUnderTest() {
        underTest = AudioQueueViewModel(
            mediaQueueItemUiEntityMapper = mediaQueueItemUiEntityMapper,
            durationInSecondsTextMapper = durationInSecondsTextMapper,
            isParticipatingInChatCallUseCase = isParticipatingInChatCallUseCase
        )
    }

    @AfterEach
    fun resetMocks() {
        reset(
            mediaQueueItemUiEntityMapper,
            durationInSecondsTextMapper,
            isParticipatingInChatCallUseCase
        )
    }

    @Test
    fun `test that the initial state is returned`() = runTest {
        underTest.uiState.test {
            val initial = awaitItem()
            assertThat(initial.items).isEmpty()
            assertThat(initial.isPaused).isFalse()
            assertThat(initial.currentPlayingPosition).isEqualTo("00:00")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test that state is updated correctly after media queue items initialised`() = runTest {
        val items = listOf(testPlaylistItem, testPlaylistItem)
        whenever(
            mediaQueueItemUiEntityMapper(
                testIcon,
                null,
                testNodeId,
                testName,
                testType,
                testDuration
            )
        ).thenReturn(mock())
        initUnderTest()
        underTest.initMediaQueueItemList(items)
        underTest.uiState.test {
            assertThat(awaitItem().items).isNotEmpty()
        }
    }

    @Test
    fun `test that state is updated correctly when updatePlaybackState is called`() = runTest {
        initUnderTest()
        underTest.uiState.test {
            assertThat(awaitItem().isPaused).isFalse()
            underTest.updatePlaybackState(true)
            assertThat(awaitItem().isPaused).isTrue()
            underTest.updatePlaybackState(false)
            assertThat(awaitItem().isPaused).isFalse()
        }
    }

    @Test
    fun `test that state is updated correctly when updateCurrentPlayingPosition is called`() =
        runTest {
            val durationString = "10:00"
            whenever(durationInSecondsTextMapper(any())).thenReturn(durationString)
            initUnderTest()
            underTest.updateCurrentPlayingPosition(0)
            underTest.uiState.test {
                assertThat(awaitItem().currentPlayingPosition).isEqualTo(durationString)
            }
        }

    @Test
    fun `test that state is updated correctly when updateMediaQueueAfterReorder is called`() =
        runTest {
            val list = (1..3).map {
                initMediaQueueItemMapperResult(it.toLong())
                getPlaylistItem(it.toLong())
            }

            initUnderTest()

            underTest.initMediaQueueItemList(list)
            underTest.updateMediaQueueAfterReorder(1, 2)
            underTest.uiState.test {
                val actual = awaitItem().items
                assertThat(actual.size).isEqualTo(3)
                assertThat(actual[1].id).isEqualTo(NodeId(3L))
                assertThat(actual[2].id).isEqualTo(NodeId(2L))
            }
        }

    private fun initMediaQueueItemMapperResult(handle: Long) {
        val nodeId = mock<NodeId> { on { longValue }.thenReturn(handle) }
        val mediaQueueItem = getMockedMediaQueueItem(nodeId)
        whenever(
            mediaQueueItemUiEntityMapper(
                icon = 0,
                thumbnailFile = null,
                id = NodeId(handle),
                name = testName,
                type = testType,
                duration = testDuration
            )
        ).thenReturn(mediaQueueItem)
    }

    private fun getMockedMediaQueueItem(
        nodeId: NodeId,
        itemType: MediaQueueItemType = testType,
    ) = mock<MediaQueueItemUiEntity> {
        on { id }.thenReturn(nodeId)
        on { type }.thenReturn(itemType)
    }

    @Test
    fun `test that state is updated correctly when updateMediaQueueAfterMediaItemTransition is called`() =
        runTest {
            val list = (1..3).map {
                val handle = it.toLong()
                initMediaQueueItemMapperResultWithCopy(
                    handle = handle,
                    itemType = when (it) {
                        1 -> MediaQueueItemType.Previous
                        2 -> MediaQueueItemType.Playing
                        else -> MediaQueueItemType.Next
                    },
                    parameterType = when (it) {
                        1 -> MediaQueueItemType.Playing
                        else -> MediaQueueItemType.Next
                    }
                )
                getPlaylistItem(handle)
            }

            initUnderTest()

            underTest.initMediaQueueItemList(list)
            underTest.updateMediaQueueAfterMediaItemTransition(0)
            underTest.uiState.test {
                val actual = awaitItem().items
                assertThat(actual.size).isEqualTo(3)
                assertThat(actual[0].type.ordinal).isEqualTo(MediaQueueItemType.Previous.ordinal)
                assertThat(actual[1].type.ordinal).isEqualTo(MediaQueueItemType.Playing.ordinal)
                assertThat(actual[2].type.ordinal).isEqualTo(MediaQueueItemType.Next.ordinal)
            }
        }

    private fun initMediaQueueItemMapperResultWithCopy(
        handle: Long,
        itemType: MediaQueueItemType,
        parameterType: MediaQueueItemType,
    ) {
        val mediaQueueItem = getMockedMediaQueueItem(NodeId(handle), itemType)
        initMediaQueueItemMapperResultByItem(handle, mediaQueueItem)
        whenever(mediaQueueItem.copy(type = parameterType)).thenReturn(mediaQueueItem)
    }

    private fun initMediaQueueItemMapperResultByItem(
        handle: Long,
        mediaQueueItemUiEntity: MediaQueueItemUiEntity,
    ) {
        whenever(
            mediaQueueItemUiEntityMapper(
                icon = 0,
                thumbnailFile = null,
                id = NodeId(handle),
                name = testName,
                type = testType,
                duration = testDuration
            )
        ).thenReturn(mediaQueueItemUiEntity)
    }
}
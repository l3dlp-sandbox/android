package mega.privacy.android.app.presentation.meeting.chat.view.message.attachment

import android.content.Intent
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mega.privacy.android.app.presentation.mapper.file.FileSizeStringMapper
import mega.privacy.android.app.presentation.node.FileNodeContent
import mega.privacy.android.app.presentation.time.mapper.DurationInSecondsTextMapper
import mega.privacy.android.core.test.extension.CoroutineMainDispatcherExtension
import mega.privacy.android.domain.entity.AudioFileTypeInfo
import mega.privacy.android.domain.entity.FileTypeInfo
import mega.privacy.android.domain.entity.GifFileTypeInfo
import mega.privacy.android.domain.entity.PdfFileTypeInfo
import mega.privacy.android.domain.entity.RawFileTypeInfo
import mega.privacy.android.domain.entity.StaticImageFileTypeInfo
import mega.privacy.android.domain.entity.SvgFileTypeInfo
import mega.privacy.android.domain.entity.TextFileTypeInfo
import mega.privacy.android.domain.entity.UnMappedFileTypeInfo
import mega.privacy.android.domain.entity.UnknownFileTypeInfo
import mega.privacy.android.domain.entity.UrlFileTypeInfo
import mega.privacy.android.domain.entity.VideoFileTypeInfo
import mega.privacy.android.domain.entity.chat.ChatMessageStatus
import mega.privacy.android.domain.entity.chat.ChatMessageType
import mega.privacy.android.domain.entity.chat.messages.NodeAttachmentMessage
import mega.privacy.android.domain.entity.node.NodeContentUri
import mega.privacy.android.domain.entity.node.chat.ChatDefaultFile
import mega.privacy.android.domain.entity.node.chat.ChatFile
import mega.privacy.android.domain.usecase.node.GetNodeContentUriUseCase
import mega.privacy.android.domain.usecase.chat.message.GetCachedOriginalPathUseCase
import mega.privacy.android.domain.usecase.chat.message.GetMessageIdsByTypeUseCase
import mega.privacy.android.domain.usecase.node.GetNodePreviewFilePathUseCase
import mega.privacy.android.domain.usecase.thumbnailpreview.GetPreviewUseCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.time.Duration.Companion.seconds

@ExtendWith(CoroutineMainDispatcherExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NodeAttachmentMessageViewModelTest {
    lateinit var underTest: NodeAttachmentMessageViewModel

    private val getPreviewUseCase = mock<GetPreviewUseCase>()
    private val fileSizeStringMapper = mock<FileSizeStringMapper>()
    private val durationInSecondsTextMapper = mock<DurationInSecondsTextMapper>()
    private val nodeContentUriIntentMapper = mock<NodeContentUriIntentMapper>()
    private val getMessageIdsByTypeUseCase = mock<GetMessageIdsByTypeUseCase>()
    private val getNodeContentUriUseCase: GetNodeContentUriUseCase = mock()
    private val getNodePreviewFilePathUseCase = mock<GetNodePreviewFilePathUseCase>()
    private val getCachedOriginalPathUseCase = mock<GetCachedOriginalPathUseCase>()

    @BeforeEach
    internal fun initTests() {
        resetMocks()
        underTest = NodeAttachmentMessageViewModel(
            getPreviewUseCase = getPreviewUseCase,
            fileSizeStringMapper = fileSizeStringMapper,
            durationInSecondsTextMapper = durationInSecondsTextMapper,
            nodeContentUriIntentMapper = nodeContentUriIntentMapper,
            getMessageIdsByTypeUseCase = getMessageIdsByTypeUseCase,
            getNodeContentUriUseCase = getNodeContentUriUseCase,
            getNodePreviewFilePathUseCase = getNodePreviewFilePathUseCase,
            getCachedOriginalPathUseCase = getCachedOriginalPathUseCase,
        )
    }

    fun resetMocks() {
        reset(
            getPreviewUseCase,
            fileSizeStringMapper,
            durationInSecondsTextMapper,
            nodeContentUriIntentMapper,
            getMessageIdsByTypeUseCase,
            getNodeContentUriUseCase,
            getNodePreviewFilePathUseCase,
            getCachedOriginalPathUseCase,
        )
    }

    @Test
    fun `test that preview is added when message is added`() = runTest {
        val expectedType = UnknownFileTypeInfo(
            mimeType = "application/jpg",
            extension = "jpg"
        )
        val fileNode = mock<ChatDefaultFile> {
            on { hasPreview } doReturn true
            on { name } doReturn "name"
            on { size } doReturn 123L
            on { type } doReturn expectedType
        }
        whenever(fileSizeStringMapper(any())).thenReturn("size")
        val expected = "file://image.jpg"
        val previewFile = mock<File> {
            on { toString() } doReturn expected
        }
        whenever(getPreviewUseCase(fileNode)).thenReturn(previewFile)
        val msg = buildNodeAttachmentMessage(fileNode)
        underTest.getOrPutUiStateFlow(msg).test {
            val actual = awaitItem().previewUri
            assertThat(actual).isEqualTo(expected)
        }
    }

    @Test
    fun `test that ui state initial state has correct name and size`() = runTest {
        val expectedName = "attachment.jpg"
        val expectedSize = "a lot of bytes"
        val expectedType = UnknownFileTypeInfo(
            mimeType = "application/jpg",
            extension = "jpg"
        )
        val fileNode = mock<ChatDefaultFile> {
            on { name } doReturn expectedName
            on { size } doReturn 123L
            on { hasPreview } doReturn false
            on { type } doReturn expectedType
        }
        whenever(fileSizeStringMapper(any())).thenReturn(expectedSize)

        val msg = buildNodeAttachmentMessage(fileNode)
        underTest.getOrPutUiStateFlow(msg).test {
            val actual = awaitItem()
            assertThat(actual.fileName).isEqualTo(expectedName)
            assertThat(actual.fileSize).isEqualTo(expectedSize)
        }
    }

    @ParameterizedTest
    @MethodSource("getFileAndVideoTypes")
    fun `test that initial ui state uses cached original file when file type is image or video`(
        fileTypeInfo: FileTypeInfo,
    ) = runTest {
        val expected = "cachedPreview"
        val fileNode = mock<ChatDefaultFile> {
            on { name } doReturn "name"
            on { size } doReturn 123L
            on { hasPreview } doReturn false
            on { type } doReturn fileTypeInfo
        }
        whenever(fileSizeStringMapper(any())).thenReturn("1byte")

        whenever(getCachedOriginalPathUseCase(fileNode)).thenReturn(expected)

        val msg = buildNodeAttachmentMessage(fileNode)
        underTest.getOrPutUiStateFlow(msg).test {
            val actual = awaitItem()
            assertThat(actual.previewUri).isEqualTo(expected)
        }
    }

    @ParameterizedTest
    @MethodSource("getNoFileNorVideoTypes")
    fun `test that initial ui state does not use cached original file when it is not a video or image`(
        fileTypeInfo: FileTypeInfo,
    ) = runTest {
        val fileNode = mock<ChatDefaultFile> {
            on { name } doReturn "name"
            on { size } doReturn 123L
            on { hasPreview } doReturn false
            on { type } doReturn fileTypeInfo
        }
        whenever(fileSizeStringMapper(any())).thenReturn("1byte")

        val msg = buildNodeAttachmentMessage(fileNode)
        underTest.getOrPutUiStateFlow(msg).test {
            val actual = awaitItem()
            assertThat(actual.previewUri).isNull()
        }
        verifyNoInteractions(getCachedOriginalPathUseCase)
    }

    @Test
    fun `test that getNodeAttachmentMessageIds returns correct ids`() = runTest {
        val expected = listOf(1L, 2L, 3L)
        whenever(getMessageIdsByTypeUseCase(CHAT_ID, ChatMessageType.NODE_ATTACHMENT)).thenReturn(
            expected
        )
        val actual = underTest.getNodeAttachmentMessageIds(CHAT_ID)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `test that getChatNodeContentUri returns correct uri`() = runTest {
        val expected = mock<NodeContentUri.LocalContentUri>()
        val fileNode = mock<ChatDefaultFile>()
        whenever(getNodeContentUriUseCase(fileNode)).thenReturn(expected)
        val msg = buildNodeAttachmentMessage(fileNode)
        val actual = underTest.getChatNodeContentUri(msg)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `test that applyNodeContentUri invokes mapper`() = runTest {
        val intent = mock<Intent>()
        val content = mock<NodeContentUri.LocalContentUri>()
        val mimeType = "mimeType"
        val isSupported = true
        underTest.applyNodeContentUri(intent, content, mimeType, isSupported)
        verify(nodeContentUriIntentMapper).invoke(intent, content, mimeType, isSupported)
    }

    @Test
    fun `test that handleFileNode returns image content correctly`() = runTest {
        val fileNode = mock<ChatDefaultFile> {
            on { type } doReturn StaticImageFileTypeInfo(
                mimeType = "image/jpg",
                extension = "jpg"
            )
        }
        val msg = buildNodeAttachmentMessage(fileNode)
        val messageIds = listOf(1L, 2L, 3L)
        whenever(getMessageIdsByTypeUseCase(CHAT_ID, ChatMessageType.NODE_ATTACHMENT)).thenReturn(
            messageIds
        )
        val actual = underTest.handleFileNode(msg)
        assertThat(actual).isEqualTo(FileNodeContent.Image(messageIds))
    }

    @Test
    fun `test that handleFileNode returns text content correctly`() = runTest {
        val fileNode = mock<ChatDefaultFile> {
            on { type } doReturn TextFileTypeInfo(
                mimeType = "text/plain",
                extension = "txt"
            )
            on { size } doReturn 123L
        }
        val msg = buildNodeAttachmentMessage(fileNode)
        val actual = underTest.handleFileNode(msg)
        assertThat(actual).isEqualTo(FileNodeContent.TextContent)
    }

    @Test
    fun `test that handleFileNode returns pdf content correctly`() = runTest {
        val fileNode = mock<ChatDefaultFile> {
            on { type } doReturn PdfFileTypeInfo
        }
        val msg = buildNodeAttachmentMessage(fileNode)
        val uri = mock<NodeContentUri.LocalContentUri>()
        whenever(getNodeContentUriUseCase(fileNode)).thenReturn(uri)
        val actual = underTest.handleFileNode(msg)
        assertThat(actual).isEqualTo(FileNodeContent.Pdf(uri))
    }

    private fun getFileAndVideoTypes(): List<FileTypeInfo> {
        val m = "mime/type"
        val e = "ext"
        return listOf(
            VideoFileTypeInfo(m, e, 18.seconds),
            GifFileTypeInfo(m, e),
            RawFileTypeInfo(m, e),
            StaticImageFileTypeInfo(m, e),
            SvgFileTypeInfo(m, e),
        )
    }

    private fun getNoFileNorVideoTypes(): List<FileTypeInfo> {
        val m = "mime/type"
        val e = "ext"
        return listOf(
            AudioFileTypeInfo(m, e, 18.seconds),
            PdfFileTypeInfo,
            TextFileTypeInfo(m, e),
            UnknownFileTypeInfo(m, e),
            UnMappedFileTypeInfo(e),
            UrlFileTypeInfo,
        )
    }

    private fun buildNodeAttachmentMessage(fileNode: ChatFile) =
        NodeAttachmentMessage(
            chatId = CHAT_ID,
            msgId = MSG_ID,
            time = 12L,
            isDeletable = false,
            isEditable = false,
            isMine = true,
            userHandle = 23L,
            shouldShowAvatar = true,
            fileNode = fileNode,
            reactions = emptyList(),
            status = ChatMessageStatus.UNKNOWN,
        )
}

private const val CHAT_ID = 44L
private const val MSG_ID = 55L
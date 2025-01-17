package mega.privacy.android.core.ui.controls.chat.messages.file

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import mega.privacy.android.core.R
import mega.privacy.android.core.ui.preview.BooleanProvider
import mega.privacy.android.core.ui.preview.CombinedThemePreviews
import mega.privacy.android.core.ui.theme.AndroidTheme

/**
 * File message with or without preview
 *
 * @param isMe whether message is sent from me
 * @param fileTypeResId resource id of file type icon
 * @param imageUri uri of the file to be loaded, usually something like ["file://xxx.xxx".toUri()]
 * @param modifier
 * @param loadProgress loading progress of the message. null if already loaded. The value can be 0-1.
 * @param fileName name of file
 * @param fileSize size string of file. It can be "Uploading" when message is loading.
 * @param onClick handle click when file message is clicked
 */

@Composable
fun FileMessageView(
    isMe: Boolean,
    fileTypeResId: Int,
    imageUri: Uri?,
    modifier: Modifier = Modifier,
    loadProgress: Float? = null,
    fileName: String = "",
    fileSize: String = "",
    onClick: () -> Unit = {},
) {
    val noPreviewContent: @Composable () -> Unit = {
        FileNoPreviewMessageView(
            isMe,
            fileTypeResId,
            modifier,
            fileName,
            fileSize,
        )
    }
    var intrinsicSize by remember {
        mutableStateOf<Size?>(null)
    }
    FileContainerMessageView(
        modifier = Modifier.padding(12.dp),
        imageIntrinsicSize = intrinsicSize,
        loadProgress = loadProgress,
        onClick = onClick
    ) {
        if (imageUri != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .crossfade(true)
                    .data(imageUri)
                    .build(),
                contentDescription = fileName,
                modifier = Modifier,
                contentScale = ContentScale.Inside,
                loading = { noPreviewContent() },
                error = { noPreviewContent() },
                success = {
                    intrinsicSize = it.painter.intrinsicSize
                    Image(
                        painter = it.painter,
                        contentDescription = "Image",
                        contentScale = ContentScale.Inside,
                        modifier = Modifier.testTag(FILE_PREVIEW_MESSAGE_VIEW_IMAGE_TEST_TAG)
                    )
                },
            )
        } else {
            noPreviewContent()
        }
    }
}

@CombinedThemePreviews
@Composable
private fun FileNoPreviewMessageViewPreview(
    @PreviewParameter(BooleanProvider::class) isMe: Boolean,
) {
    AndroidTheme(isDark = isSystemInDarkTheme()) {
        val context = LocalContext.current
        val resourceId = R.drawable.ic_check_circle
        //this uri will be loaded if the preview is running on the device
        val resourceUri = remember(resourceId) {
            with(context.resources) {
                Uri.Builder()
                    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(getResourcePackageName(resourceId))
                    .appendPath(getResourceTypeName(resourceId))
                    .appendPath(getResourceEntryName(resourceId))
                    .build()
            }
        }
        FileMessageView(
            isMe = isMe,
            fileTypeResId = R.drawable.ic_alert_circle,
            imageUri = resourceUri,
            loadProgress = 0.6f.takeIf { isMe },
            fileName = "Hello.pdf",
            fileSize = "30 MB",
        )
    }
}

internal const val FILE_PREVIEW_MESSAGE_VIEW_IMAGE_TEST_TAG = "chat_file_preview_message_view:image"

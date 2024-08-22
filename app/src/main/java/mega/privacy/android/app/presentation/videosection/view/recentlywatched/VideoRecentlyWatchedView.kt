package mega.privacy.android.app.presentation.videosection.view.recentlywatched

import mega.privacy.android.icon.pack.R as iconPackR
import mega.privacy.android.shared.resources.R as shareR
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import mega.privacy.android.app.presentation.videosection.model.VideoSectionMenuAction
import mega.privacy.android.app.presentation.videosection.model.VideoUIEntity
import mega.privacy.android.app.presentation.videosection.view.allvideos.VideoItemView
import mega.privacy.android.app.utils.MegaNodeUtil
import mega.privacy.android.core.formatter.formatFileSize
import mega.privacy.android.domain.entity.node.thumbnail.ThumbnailRequest
import mega.privacy.android.legacy.core.ui.controls.LegacyMegaEmptyViewWithImage
import mega.privacy.android.shared.original.core.ui.controls.appbar.AppBarType
import mega.privacy.android.shared.original.core.ui.controls.appbar.MegaAppBar
import mega.privacy.android.shared.original.core.ui.controls.text.MegaText
import mega.privacy.android.shared.original.core.ui.theme.values.TextColor
import nz.mega.sdk.MegaNode

/**
 * The view for the video recently watched.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun VideoRecentlyWatchedView(
    group: Map<String, List<VideoUIEntity>>,
    modifier: Modifier,
    onBackPressed: () -> Unit,
    onClick: (item: VideoUIEntity, index: Int) -> Unit,
    onActionPressed: (VideoSectionMenuAction?) -> Unit,
    onMenuClick: (VideoUIEntity) -> Unit,
) {
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        scaffoldState = rememberScaffoldState(),
        topBar = {
            MegaAppBar(
                appBarType = AppBarType.BACK_NAVIGATION,
                title = stringResource(id = shareR.string.video_section_title_video_recently_watched),
                modifier = Modifier.testTag(VIDEO_RECENTLY_WATCHED_TOP_BAR_TEST_TAG),
                actions = listOf(VideoSectionMenuAction.VideoRecentlyWatchedClearAction),
                onActionPressed = { onActionPressed(it as? VideoSectionMenuAction) },
                onNavigationPressed = onBackPressed,
            )
        }
    ) { paddingValue ->
        Column {
            when {
                group.isEmpty() -> LegacyMegaEmptyViewWithImage(
                    modifier = Modifier.testTag(VIDEO_RECENTLY_WATCHED_EMPTY_TEST_TAG),
                    text = stringResource(id = shareR.string.video_section_empty_hint_no_recently_activity),
                    imagePainter = painterResource(id = iconPackR.drawable.ic_empty_recently_watched)
                )

                else -> {
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.padding(paddingValue)
                    ) {
                        group.forEach { (date, items) ->
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colors.surface)
                                        .height(36.dp)
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    MegaText(
                                        text = date,
                                        textColor = TextColor.Primary,
                                        modifier = Modifier.testTag(
                                            VIDEO_RECENTLY_WATCHED_HEADER_TEST_TAG
                                        )
                                    )
                                }
                            }

                            items(count = items.size, key = { items[it].id.longValue }) {
                                val videoItem = items[it]
                                VideoItemView(
                                    icon = iconPackR.drawable.ic_video_section_video_default_thumbnail,
                                    name = videoItem.name,
                                    fileSize = formatFileSize(videoItem.size, LocalContext.current),
                                    duration = videoItem.durationString,
                                    isFavourite = videoItem.isFavourite,
                                    isSelected = videoItem.isSelected,
                                    isSharedWithPublicLink = videoItem.isSharedItems,
                                    labelColor = if (videoItem.label != MegaNode.NODE_LBL_UNKNOWN)
                                        colorResource(
                                            id = MegaNodeUtil.getNodeLabelColor(
                                                videoItem.label
                                            )
                                        ) else null,
                                    thumbnailData = ThumbnailRequest(videoItem.id),
                                    nodeAvailableOffline = videoItem.nodeAvailableOffline,
                                    onClick = { onClick(videoItem, it) },
                                    onMenuClick = { onMenuClick(videoItem) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal const val videoRecentlyWatchedRoute = "videoSection/video_recently_watched"
internal const val VIDEO_RECENTLY_WATCHED_HEADER_TEST_TAG =
    "video_recently_watched_view:header_text"
internal const val VIDEO_RECENTLY_WATCHED_TOP_BAR_TEST_TAG = "video_recently_watched_view:top_bar"
internal const val VIDEO_RECENTLY_WATCHED_EMPTY_TEST_TAG = "video_recently_watched_view:empty_view"
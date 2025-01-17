package mega.privacy.android.app.presentation.node.view

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import mega.privacy.android.app.MimeTypeList
import mega.privacy.android.app.presentation.node.NodeBottomSheetActionHandler
import mega.privacy.android.app.presentation.node.NodeOptionsBottomSheetViewModel
import mega.privacy.android.app.presentation.view.extension.fileInfo
import mega.privacy.android.app.presentation.view.extension.folderInfo
import mega.privacy.android.app.presentation.view.extension.getIcon
import mega.privacy.android.core.ui.controls.lists.NodeListViewItem
import mega.privacy.android.core.ui.controls.sheets.BottomSheet
import mega.privacy.android.domain.entity.node.FileNode
import mega.privacy.android.domain.entity.node.FolderNode
import mega.privacy.android.domain.entity.node.TypedFolderNode
import mega.privacy.android.domain.entity.node.TypedNode
import mega.privacy.android.domain.entity.node.thumbnail.ThumbnailRequest


/**
 * Node options bottom sheet
 *
 * @param modalSheetState
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun NodeOptionsBottomSheet(
    modalSheetState: ModalBottomSheetState,
    node: TypedNode,
    handler: NodeBottomSheetActionHandler,
    navHostController: NavHostController,
    onDismiss: () -> Unit,
    viewModel: NodeOptionsBottomSheetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.getBottomSheetOptions(node)
    }
    BottomSheet(
        modalSheetState = modalSheetState,
        sheetHeader = {
            NodeListViewItem(
                title = node.name,
                subtitle = when (node) {
                    is FileNode -> node.fileInfo()
                    is FolderNode -> node.folderInfo()
                    else -> ""
                },
                icon = node.let { node -> node as? TypedFolderNode }?.getIcon()
                    ?: MimeTypeList.typeForName(node.name).iconResourceId,
                thumbnailData = ThumbnailRequest(node.id),
            )
        },
        sheetBody = {
            LazyColumn {
                val groups = state.actions.groupBy { it.group }
                groups.toSortedMap()
                    .mapValues { (_, list) ->
                        list.sortedBy { it.orderInGroup }
                    }
                    .values
                    .forEachIndexed { index, actions ->
                        items(actions) { item: BottomSheetMenuItem ->
                            item.control(onDismiss, handler::handleAction, navHostController)
                        }

                        if (index < state.actions.size - 1 && index != groups.size - 1) {
                            item {
                                Divider(
                                    modifier = Modifier
                                        .padding(start = 72.dp)
                                        .testTag("$DIVIDER_TAG$index")
                                )
                            }
                        }
                    }
            }
        },
    )
}

internal const val DIVIDER_TAG = "node_options_bottom_sheet:divider"

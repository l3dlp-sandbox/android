package mega.privacy.android.app.presentation.node.view.bottomsheetmenuitems

import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mega.privacy.android.app.presentation.node.model.menuaction.AvailableOfflineMenuAction
import mega.privacy.android.core.ui.model.MenuAction
import mega.privacy.android.core.ui.model.MenuActionWithIcon
import mega.privacy.android.domain.entity.node.TypedNode
import mega.privacy.android.domain.entity.shares.AccessPermission
import mega.privacy.android.domain.qualifier.ApplicationScope
import mega.privacy.android.domain.usecase.foldernode.IsFolderEmptyUseCase
import mega.privacy.android.domain.usecase.offline.RemoveOfflineNodeUseCase
import mega.privacy.android.legacy.core.ui.controls.controlssliders.MegaSwitch
import mega.privacy.android.legacy.core.ui.controls.lists.MenuActionListTile
import timber.log.Timber
import javax.inject.Inject

/**
 * Available offline menu item
 *
 * @param menuAction [AvailableOfflineMenuAction]
 */
class AvailableOfflineBottomSheetMenuItem @Inject constructor(
    override val menuAction: AvailableOfflineMenuAction,
    private val removeOfflineNodeUseCase: RemoveOfflineNodeUseCase,
    private val isFolderEmptyUseCase: IsFolderEmptyUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) : NodeBottomSheetMenuItem<MenuActionWithIcon> {

    override fun buildComposeControl(
        selectedNode: TypedNode,
    ): BottomSheetClickHandler =
        { onDismiss, handler, navController ->
            val onClick = getOnClickFunction(
                node = selectedNode,
                onDismiss = onDismiss,
                actionHandler = handler,
                navController = navController,
            )
            MenuActionListTile(
                text = menuAction.getDescription(),
                icon = menuAction.getIconPainter(),
                isDestructive = isDestructiveAction,
                onActionClicked = onClick,
                addSeparator = false,
                trailingItem = {
                    MegaSwitch(
                        checked = selectedNode.isAvailableOffline,
                        onCheckedChange = { onClick() },
                    )
                }
            )
        }

    override suspend fun shouldDisplay(
        isNodeInRubbish: Boolean,
        accessPermission: AccessPermission?,
        isInBackups: Boolean,
        node: TypedNode,
        isConnected: Boolean,
    ) = isNodeInRubbish.not() && node.isTakenDown.not() && isFolderEmptyUseCase(node).not()

    override fun getOnClickFunction(
        node: TypedNode,
        onDismiss: () -> Unit,
        actionHandler: (menuAction: MenuAction, node: TypedNode) -> Unit,
        navController: NavHostController,
    ): () -> Unit = {
        onDismiss()
        if (node.isAvailableOffline) {
            scope.launch {
                runCatching {
                    removeOfflineNodeUseCase(nodeId = node.id)
                }.onFailure { Timber.e(it) }
            }
        } else {
            Timber.d("Save offline")
        }
    }

    override val groupId = 6
}
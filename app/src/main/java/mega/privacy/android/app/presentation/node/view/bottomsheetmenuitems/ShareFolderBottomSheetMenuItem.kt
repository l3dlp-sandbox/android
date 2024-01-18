package mega.privacy.android.app.presentation.node.view.bottomsheetmenuitems

import android.content.Intent
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mega.privacy.android.app.di.CoroutineScopesModule
import mega.privacy.android.app.main.AddContactActivity
import mega.privacy.android.app.main.FileContactListActivity
import mega.privacy.android.app.presentation.extensions.isOutShare
import mega.privacy.android.app.presentation.node.model.menuaction.ShareFolderMenuAction
import mega.privacy.android.app.presentation.search.navigation.searchFolderShareDialog
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.core.ui.model.MenuAction
import mega.privacy.android.core.ui.model.MenuActionWithIcon
import mega.privacy.android.domain.entity.node.TypedFolderNode
import mega.privacy.android.domain.entity.node.TypedNode
import mega.privacy.android.domain.entity.node.backup.BackupNodeType
import mega.privacy.android.domain.entity.shares.AccessPermission
import mega.privacy.android.domain.qualifier.ApplicationScope
import mega.privacy.android.domain.qualifier.MainDispatcher
import mega.privacy.android.domain.usecase.node.GetNodeByHandleUseCase
import mega.privacy.android.domain.usecase.node.backup.CheckBackupNodeTypeByHandleUseCase
import mega.privacy.android.domain.usecase.shares.CreateShareKeyUseCase
import javax.inject.Inject

/**
 * Share folder bottom sheet menu item
 *
 * @param menuAction [ShareFolderMenuAction]
 */
class ShareFolderBottomSheetMenuItem @Inject constructor(
    override val menuAction: ShareFolderMenuAction,
    @ApplicationScope private val scope: CoroutineScope,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    private val createShareKeyUseCase: CreateShareKeyUseCase,
    private val checkBackupNodeTypeByHandleUseCase: CheckBackupNodeTypeByHandleUseCase,
) : NodeBottomSheetMenuItem<MenuActionWithIcon> {
    override suspend fun shouldDisplay(
        isNodeInRubbish: Boolean,
        accessPermission: AccessPermission?,
        isInBackups: Boolean,
        node: TypedNode,
        isConnected: Boolean,
    ) = node.isTakenDown.not()
            && node is TypedFolderNode
            && isNodeInRubbish.not()
            && node.isOutShare().not()


    override fun getOnClickFunction(
        node: TypedNode,
        onDismiss: () -> Unit,
        actionHandler: (menuAction: MenuAction, node: TypedNode) -> Unit,
        navController: NavHostController,
    ): () -> Unit = {
        scope.launch(mainDispatcher) {
            onDismiss()
            val context = navController.context
            if (node is TypedFolderNode) {
                createShareKeyUseCase(node)
                val backupType = checkBackupNodeTypeByHandleUseCase(node)
                if (backupType != BackupNodeType.NonBackupNode) {
                    navController.navigate(
                        searchFolderShareDialog.plus("/${node.id.longValue}").plus("/${false}")
                    )
                } else {
                    val intent = Intent().apply {
                        setClass(context, AddContactActivity::class.java)
                        putExtra("contactType", Constants.CONTACT_TYPE_BOTH)
                        putExtra("MULTISELECT", 0)
                        putExtra(AddContactActivity.EXTRA_NODE_HANDLE, node.id.longValue)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    override val groupId = 7
}
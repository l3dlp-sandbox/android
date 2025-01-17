package mega.privacy.android.app.presentation.node.model.toolbarmenuitems

import mega.privacy.android.app.presentation.extensions.isOutShare
import mega.privacy.android.app.presentation.node.model.menuaction.RemoveShareMenuAction
import mega.privacy.android.core.ui.model.MenuActionWithIcon
import mega.privacy.android.domain.entity.node.TypedNode
import javax.inject.Inject

/**
 * Remove share menu item
 */
class RemoveShare @Inject constructor() : NodeToolbarMenuItem<MenuActionWithIcon> {

    override fun shouldDisplay(
        hasNodeAccessPermission: Boolean,
        selectedNodes: Set<TypedNode>,
        canBeMovedToTarget: Boolean,
        noNodeInBackups: Boolean,
        noNodeTakenDown: Boolean,
        allFileNodes: Boolean,
        resultCount: Int,
    ): Boolean = selectedNodes.isNotEmpty() && selectedNodes.all { it.isOutShare() }

    override val menuAction = RemoveShareMenuAction(210)

}
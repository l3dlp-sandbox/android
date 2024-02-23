package test.mega.privacy.android.app.presentation.node.model.toolbarmenuitems

import com.google.common.truth.Truth
import mega.privacy.android.app.presentation.node.model.menuaction.ShareFolderMenuAction
import mega.privacy.android.app.presentation.node.model.toolbarmenuitems.ShareFolderToolbarMenuItem
import mega.privacy.android.domain.entity.node.TypedFileNode
import mega.privacy.android.domain.entity.node.TypedFolderNode
import mega.privacy.android.domain.entity.node.TypedNode
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShareFolderToolbarMenuItemTest {

    private val underTest = ShareFolderToolbarMenuItem(
        checkBackupNodeTypeByHandleUseCase = mock(),
        listToStringWithDelimitersMapper = mock(),
        menuAction = ShareFolderMenuAction()
    )

    private val oneFileNodeSelected = mock<TypedFileNode> {
        on { isTakenDown }.thenReturn(false)
    }
    private val oneFolderNodeSelected = mock<TypedFolderNode>()
    private val multipleNodes = listOf(oneFileNodeSelected, oneFolderNodeSelected)

    @ParameterizedTest(name = "when noNodeTakenDown: {0} and selected nodes are {1} then visibility is {2}")
    @MethodSource("provideArguments")
    fun `test that share folder item visibility is updated`(
        noNodeTakenDown: Boolean,
        selectedNodes: List<TypedNode>,
        expected: Boolean,
    ) {
        val result = underTest.shouldDisplay(
            hasNodeAccessPermission = false,
            selectedNodes = selectedNodes,
            canBeMovedToTarget = false,
            noNodeInBackups = false,
            noNodeTakenDown = noNodeTakenDown,
            allFileNodes = false,
            resultCount = 10
        )
        Truth.assertThat(result).isEqualTo(expected)
    }

    private fun provideArguments() = Stream.of(
        Arguments.of(false, multipleNodes, false),
        Arguments.of(true, multipleNodes, false),
        Arguments.of(true, listOf(oneFileNodeSelected), false),
        Arguments.of(true, listOf(oneFolderNodeSelected), true),
        Arguments.of(false, listOf(oneFolderNodeSelected), false)
    )
}
package mega.privacy.android.feature.sync.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.StateFlow
import mega.privacy.android.feature.sync.R
import mega.privacy.android.feature.sync.domain.entity.RemoteFolder
import mega.privacy.android.feature.sync.ui.newfolderpair.SyncNewFolderScreenRoute
import mega.privacy.android.feature.sync.ui.newfolderpair.SyncNewFolderState
import mega.privacy.android.feature.sync.ui.newfolderpair.SyncNewFolderViewModel
import mega.privacy.android.feature.sync.ui.permissions.SyncPermissionsManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "fr-rFr-w1080dp-h1920dp")
class SyncNewFolderScreenRouteTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModel: SyncNewFolderViewModel = mock()
    private val state: StateFlow<SyncNewFolderState> = mock()
    private val syncPermissionsManager: SyncPermissionsManager = mock()

    @Test
    fun `test that all sync Sync New Folder components are visible`() {
        whenever(state.value).thenReturn(SyncNewFolderState())
        whenever(viewModel.state).thenReturn(state)
        composeTestRule.setContent {
            SyncNewFolderScreenRoute(
                viewModel,
                syncPermissionsManager = syncPermissionsManager,
                openNextScreen = {},
                openSelectMegaFolderScreen = {}
            )
        }

        composeTestRule.onNodeWithText("Device folder")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("MEGA folder")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Method")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Two way sync")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Sync")
            .assertIsDisplayed()
    }

    @Test
    fun `test that entered folder pair name is correctly displayed`() {
        val folderPairName = "some_folder_pair"
        whenever(state.value).thenReturn(SyncNewFolderState(folderPairName = folderPairName))
        whenever(viewModel.state).thenReturn(state)
        composeTestRule.setContent {
            SyncNewFolderScreenRoute(
                viewModel,
                syncPermissionsManager = syncPermissionsManager,
                openNextScreen = {},
                openSelectMegaFolderScreen = {}
            )
        }

        composeTestRule.onNodeWithText(folderPairName)
            .assertIsDisplayed()
    }

    @Test
    fun `test that selected local folder name is correctly displayed`() {
        val localFolderName = "local_folder"
        whenever(state.value).thenReturn(SyncNewFolderState(selectedLocalFolder = localFolderName))
        whenever(viewModel.state).thenReturn(state)
        composeTestRule.setContent {
            SyncNewFolderScreenRoute(
                viewModel,
                syncPermissionsManager = syncPermissionsManager,
                openNextScreen = {},
                openSelectMegaFolderScreen = {}
            )
        }

        composeTestRule.onNodeWithText(localFolderName)
            .assertIsDisplayed()
    }

    @Test
    fun `test that selected MEGA folder name is correctly displayed`() {
        val megaFolderName = "mega_folder"
        whenever(state.value).thenReturn(
            SyncNewFolderState(
                selectedMegaFolder = RemoteFolder(
                    id = 0,
                    megaFolderName
                )
            )
        )
        whenever(viewModel.state).thenReturn(state)
        composeTestRule.setContent {
            SyncNewFolderScreenRoute(
                viewModel,
                openNextScreen = {},
                syncPermissionsManager = syncPermissionsManager,
                openSelectMegaFolderScreen = {}
            )
        }

        composeTestRule.onNodeWithText(megaFolderName)
            .assertIsDisplayed()
    }

    @Test
    fun `test that sync button is not clickable when local folder or mega folder are not filled`() {
        val emptyState = SyncNewFolderState()
        val openNextScreenCallback = mock<(SyncNewFolderState) -> Unit>()
        whenever(state.value).thenReturn(emptyState)
        whenever(viewModel.state).thenReturn(state)
        composeTestRule.setContent {
            SyncNewFolderScreenRoute(
                viewModel,
                syncPermissionsManager = syncPermissionsManager,
                openNextScreen = openNextScreenCallback,
                openSelectMegaFolderScreen = {}
            )
        }

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.sync_button_label))
            .performClick()

        verifyNoInteractions(openNextScreenCallback)
    }

    @Test
    fun `test that click on select mega folder button invokes openSelectMegaFolderScreen lambda`() {
        val emptyState = SyncNewFolderState()
        val openSelectMegaFolderScreenLambda = mock<() -> Unit>()
        whenever(state.value).thenReturn(emptyState)
        whenever(viewModel.state).thenReturn(state)
        composeTestRule.setContent {
            SyncNewFolderScreenRoute(
                viewModel,
                openNextScreen = {},
                syncPermissionsManager = syncPermissionsManager,
                openSelectMegaFolderScreen = openSelectMegaFolderScreenLambda
            )
        }

        composeTestRule.onNodeWithText("MEGA folder")
            .performClick()

        verify(openSelectMegaFolderScreenLambda).invoke()
    }

    @Test
    fun `test that only all files access banner is shown when all files access permission is not granted`() {
        val emptyState = SyncNewFolderState()
        whenever(state.value).thenReturn(emptyState)
        whenever(viewModel.state).thenReturn(state)
        whenever(syncPermissionsManager.isManageExternalStoragePermissionGranted())
            .thenReturn(false)
        whenever(syncPermissionsManager.isDisableBatteryOptimizationGranted(composeTestRule.activity))
            .thenReturn(false)
        composeTestRule.setContent {
            SyncNewFolderScreenRoute(
                viewModel,
                openNextScreen = {},
                syncPermissionsManager = syncPermissionsManager,
                openSelectMegaFolderScreen = { }
            )
        }

        composeTestRule.onNodeWithText("We need to access your device storage in order to sync your local folder. Click here to grant access.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Battery optimisation permission allows MEGA to run " +
                    "in the background. You can change this any time by going to " +
                    "Settings -> Apps."
        )
            .assertDoesNotExist()
    }

    @Test
    fun `test that only battery optimization banner is shown when disable battery optimization permission is not granted`() {
        val emptyState = SyncNewFolderState()
        whenever(state.value).thenReturn(emptyState)
        whenever(viewModel.state).thenReturn(state)
        whenever(syncPermissionsManager.isManageExternalStoragePermissionGranted())
            .thenReturn(true)
        whenever(syncPermissionsManager.isDisableBatteryOptimizationGranted(composeTestRule.activity))
            .thenReturn(false)
        composeTestRule.setContent {
            SyncNewFolderScreenRoute(
                viewModel,
                openNextScreen = {},
                syncPermissionsManager = syncPermissionsManager,
                openSelectMegaFolderScreen = { }
            )
        }

        composeTestRule.onNodeWithText("We need to access your device storage in order to sync your local folder. Click here to grant access.")
            .assertDoesNotExist()
        composeTestRule.onNodeWithText(
            "Battery optimisation permission allows MEGA to run " +
                    "in the background. You can change this any time by going to " +
                    "Settings -> Apps."
        )
            .assertIsDisplayed()
    }
}
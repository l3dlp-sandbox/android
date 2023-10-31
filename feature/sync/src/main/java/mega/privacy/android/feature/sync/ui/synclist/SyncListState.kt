package mega.privacy.android.feature.sync.ui.synclist

import mega.privacy.android.feature.sync.ui.model.SyncUiItem

internal data class SyncListState(
    val stalledIssuesCount: Int = 0,
    val snackbarMessage: String? = null,
)
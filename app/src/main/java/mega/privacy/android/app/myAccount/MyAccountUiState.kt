package mega.privacy.android.app.myAccount

import androidx.annotation.StringRes
import mega.privacy.android.domain.entity.Feature

/**
 * My account ui state
 *
 * @property isFileVersioningEnabled
 * @property versionsInfo
 * @property name
 * @property email
 * @property changeEmailResult
 * @property isLoading
 * @property changeUserNameResult
 * @property verifiedPhoneNumber
 * @property canVerifyPhoneNumber
 * @property isBusinessAccount
 * @property enabledFeatureFlags
 * @property backupStorageSize
 * @property cancelAccountErrorMessage The Error Message when an issue occurs during the Account
 * cancellation process
 */
data class MyAccountUiState(
    val isFileVersioningEnabled: Boolean = true,
    val name: String = "",
    val email: String = "",
    val versionsInfo: String? = null,
    val changeEmailResult: Result<String>? = null,
    val isLoading: Boolean = false,
    val changeUserNameResult: Result<Unit>? = null,
    val verifiedPhoneNumber: String? = null,
    val canVerifyPhoneNumber: Boolean = false,
    val isBusinessAccount: Boolean = false,
    val enabledFeatureFlags: Set<Feature> = emptySet(),
    val backupStorageSize: Long = 0L,
    @StringRes val cancelAccountErrorMessage: Int? = null
)

package mega.privacy.android.app.data.gateway.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Gateway for interaction with Preferences
 */
interface FeatureFlagPreferencesGateway {

    /**
     * Sets feature value to true or false
     *
     * @param featureName : name of the feature
     * @param isEnabled: Boolean value
     */
    suspend fun setFeature(featureName: String, isEnabled: Boolean)

    /**
     * Gets all features
     *
     * @return Flow of Map of feature name & boolean value
     */
    suspend fun getAllFeatures(): Flow<Map<String, Boolean>>
}
package mega.privacy.android.app.presentation.login.view

import mega.privacy.android.shared.resources.R as SharedRes
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import de.palm.composestateevents.EventEffect
import kotlinx.coroutines.delay
import mega.privacy.android.app.R
import mega.privacy.android.app.featuretoggle.AppFeatures
import mega.privacy.android.app.presentation.apiserver.view.ChangeApiServerDialog
import mega.privacy.android.app.presentation.extensions.login.error
import mega.privacy.android.app.presentation.login.model.LoginError
import mega.privacy.android.app.presentation.login.model.LoginState
import mega.privacy.android.app.presentation.login.model.MultiFactorAuthState
import mega.privacy.android.app.presentation.twofactorauthentication.view.TwoFactorAuthenticationField
import mega.privacy.android.domain.entity.Progress
import mega.privacy.android.domain.entity.account.AccountSession
import mega.privacy.android.domain.entity.login.FetchNodesUpdate
import mega.privacy.android.domain.entity.login.TemporaryWaitingError
import mega.privacy.android.legacy.core.ui.controls.appbar.SimpleTopAppBar
import mega.privacy.android.shared.original.core.ui.controls.buttons.RaisedDefaultMegaButton
import mega.privacy.android.shared.original.core.ui.controls.buttons.TextMegaButton
import mega.privacy.android.shared.original.core.ui.controls.progressindicator.MegaAnimatedLinearProgressIndicator
import mega.privacy.android.shared.original.core.ui.controls.progressindicator.MegaCircularProgressIndicator
import mega.privacy.android.shared.original.core.ui.controls.textfields.LabelTextField
import mega.privacy.android.shared.original.core.ui.controls.textfields.PasswordTextField
import mega.privacy.android.shared.original.core.ui.theme.OriginalTempTheme
import mega.privacy.android.shared.original.core.ui.theme.extensions.conditional
import mega.privacy.android.shared.original.core.ui.theme.extensions.textColorPrimary
import mega.privacy.android.shared.original.core.ui.theme.extensions.textColorSecondary

/**
 * Login fragment view.
 *
 * @param state                     [LoginState]
 * @param onEmailChanged            Action when the typed email changes.
 * @param onPasswordChanged         Action when the typed password changes.
 * @param onLoginClicked            Action when Login is pressed.
 * @param onForgotPassword          Action when Forgot password is pressed.
 * @param onCreateAccount           Action when Create account is pressed.
 * @param onSnackbarMessageConsumed Action when the snackbar message was consumed.
 * @param on2FAPinChanged           Action when one of the 2FA's pins changed.
 * @param on2FAChanged              Action when a 2FA code was pasted.
 * @param onLostAuthenticatorDevice Action when Lost authenticator device is pressed.
 * @param onBackPressed             Action when back is pressed.
 * @param onFirstTime2FAConsumed    Action when the 2FA is shown for the first time.
 * @param modifier                  [Modifier]
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginView(
    state: LoginState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClicked: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    onSnackbarMessageConsumed: () -> Unit,
    on2FAPinChanged: (String, Int) -> Unit,
    on2FAChanged: (String) -> Unit,
    onLostAuthenticatorDevice: () -> Unit,
    onBackPressed: () -> Unit,
    onFirstTime2FAConsumed: () -> Unit,
    onReportIssue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scaffoldState = rememberScaffoldState()
    var showChangeApiServerDialog by rememberSaveable { mutableStateOf(false) }
    val showLoginInProgress =
        state.isLoginInProgress || state.fetchNodesUpdate != null || state.isRequestStatusInProgress
    Scaffold(
        modifier = modifier
            .conditional(!showLoginInProgress) {
                systemBarsPadding()
            }
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
        scaffoldState = scaffoldState,
        topBar = {
            if (state.is2FARequired || state.multiFactorAuthState != null) {
                SimpleTopAppBar(
                    titleId = R.string.login_verification,
                    elevation = scrollState.value > 0,
                    onBackPressed = onBackPressed
                )
            }
        },
    ) { paddingValues ->
        with(state) {
            when {
                showLoginInProgress -> LoginInProgress(
                    state = this,
                    paddingValues = paddingValues
                )

                isLoginRequired -> RequireLogin(
                    state = this,
                    onEmailChanged = onEmailChanged,
                    onPasswordChanged = onPasswordChanged,
                    onLoginClicked = onLoginClicked,
                    onForgotPassword = onForgotPassword,
                    onCreateAccount = onCreateAccount,
                    paddingValues = paddingValues,
                    onChangeApiServer = { showChangeApiServerDialog = true },
                    onReportIssue = onReportIssue,
                    modifier = modifier,
                )

                is2FARequired || multiFactorAuthState != null -> TwoFactorAuthentication(
                    state = this,
                    paddingValues = paddingValues,
                    on2FAPinChanged = on2FAPinChanged,
                    on2FAChanged = on2FAChanged,
                    onLostAuthenticatorDevice = onLostAuthenticatorDevice,
                    onFirstTime2FAConsumed = onFirstTime2FAConsumed
                )
            }
        }

        BackHandler { onBackPressed() }

        SnackbarHost(modifier = Modifier.padding(8.dp), hostState = snackbarHostState)

        val context = LocalContext.current

        EventEffect(
            event = state.snackbarMessage,
            onConsumed = onSnackbarMessageConsumed
        ) {
            scaffoldState.snackbarHostState.showSnackbar(
                message = context.resources.getString(it),
                duration = SnackbarDuration.Long
            )
        }

        if (showChangeApiServerDialog) {
            ChangeApiServerDialog(onDismissRequest = { showChangeApiServerDialog = false })
        }
    }
}

@Composable
private fun RequireLogin(
    state: LoginState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClicked: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    paddingValues: PaddingValues,
    onChangeApiServer: () -> Unit,
    onReportIssue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(paddingValues)
            .verticalScroll(scrollState)
    ) {
        val emailFocusRequester = remember { FocusRequester() }
        val passwordFocusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current

        Text(
            modifier = Modifier
                .padding(start = 22.dp, top = 17.dp, end = 22.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        val downTime = System.currentTimeMillis()
                        tryAwaitRelease()
                        val upTime = System.currentTimeMillis()
                        if (upTime - downTime >= LONG_PRESS_DELAY) {
                            onChangeApiServer()
                        }
                    })
                }
                .testTag(LOGIN_TO_MEGA_TAG),
            text = stringResource(id = R.string.login_to_mega),
            style = MaterialTheme.typography.subtitle1.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.textColorPrimary
            ),
        )
        LabelTextField(
            onTextChange = onEmailChanged,
            label = stringResource(id = R.string.email_text),
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
            modifier = Modifier
                .padding(start = 22.dp, top = 44.dp, end = 22.dp)
                .focusRequester(emailFocusRequester)
                .focusProperties {
                    next = passwordFocusRequester
                    previous = emailFocusRequester
                }
                .testTag(TEXT_FIELD_LABEL_TAG),
            text = state.accountSession?.email ?: "",
            errorText = state.emailError?.let { stringResource(id = it.error) },
            isEmail = true
        )
        PasswordTextField(
            onTextChange = onPasswordChanged,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = {
                clickLogin(
                    onLoginClicked,
                    focusManager
                )
            }),
            modifier = Modifier
                .padding(
                    start = 22.dp,
                    top = if (state.emailError != null) 24.dp else 44.dp,
                    end = 22.dp
                )
                .focusRequester(passwordFocusRequester)
                .focusProperties {
                    next = passwordFocusRequester
                    previous = emailFocusRequester
                }
                .testTag(PASSWORD_TEXT_FIELD_TAG),
            text = state.password ?: "",
            errorText = state.passwordError?.let { stringResource(id = it.error) }
        )
        TextMegaButton(
            modifier = Modifier
                .padding(
                    start = 14.dp,
                    top = if (state.passwordError != null) 24.dp else 44.dp
                )
                .testTag(FORGOT_PASSWORD_TAG),
            textId = R.string.forgot_pass,
            onClick = onForgotPassword
        )
        RaisedDefaultMegaButton(
            modifier = Modifier
                .padding(
                    start = 22.dp,
                    top = 44.dp,
                    end = 22.dp
                )
                .fillMaxWidth()
                .testTag(LOGIN_BUTTON_TAG),
            textId = R.string.login_text,
            onClick = { clickLogin(onLoginClicked, focusManager) },
            enabled = !state.isLocalLogoutInProgress
        )
        Row(
            modifier = Modifier
                .padding(start = 22.dp, top = 10.dp, end = 22.dp)
                .alpha(if (state.isLocalLogoutInProgress) 1f else 0f)
        ) {
            MegaCircularProgressIndicator(
                modifier = Modifier
                    .size(16.dp)
                    .padding(start = 3.dp)
                    .testTag(CANCEL_LOGIN_PROGRESS_TAG),
                strokeWidth = 1.dp
            )
            Text(
                text = stringResource(id = R.string.login_in_progress),
                modifier = Modifier
                    .padding(start = 6.dp)
                    .testTag(CANCELLING_LOGIN_TAG),
                style = MaterialTheme.typography.caption.copy(color = MaterialTheme.colors.secondary)
            )
        }
        if (state.enabledFlags.contains(AppFeatures.LoginReportIssueButton)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onReportIssue()
                    }
                    .padding(start = 22.dp, top = 18.dp, end = 22.dp),
            ) {
                Text(
                    modifier = Modifier.testTag(TROUBLE_LOGIN_TAG),
                    text = stringResource(id = R.string.general_login_label_trouble_logging_in),
                    style = MaterialTheme.typography.subtitle2.copy(color = MaterialTheme.colors.textColorPrimary)
                )
            }
        }
        Row(
            modifier = Modifier.padding(end = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier
                    .padding(start = 22.dp)
                    .testTag(NEW_TO_MEGA_TAG),
                text = stringResource(id = R.string.new_to_mega),
                style = MaterialTheme.typography.subtitle2.copy(color = MaterialTheme.colors.textColorPrimary),
            )
            TextMegaButton(
                modifier = Modifier
                    .testTag(CREATE_ACCOUNT_TAG),
                textId = R.string.create_account,
                onClick = onCreateAccount
            )
        }
    }
}


private fun clickLogin(onLoginClicked: () -> Unit, focusManager: FocusManager) {
    focusManager.clearFocus(true)
    onLoginClicked()
}

@Composable
private fun LoginInProgress(
    state: LoginState,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val isInLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(paddingValues)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState),
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_splash_logo),
            contentDescription = stringResource(id = R.string.login_to_mega),
            modifier = Modifier
                .align(Alignment.Center)
                .size(288.dp)
                .testTag(MEGA_LOGO_TEST_TAG),
            contentScale = ContentScale.FillBounds
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 56.dp, end = 56.dp)
                    .widthIn(max = 300.dp)
            ) {
                MegaAnimatedLinearProgressIndicator(
                    indicatorProgress = state.currentProgress,
                    progressAnimDuration = if (state.currentProgress > 0.5f) 1000 else 3000,
                    modifier = Modifier
                        .testTag(FETCH_NODES_PROGRESS_TEST_TAG)
                )

                if (state.isRequestStatusInProgress) {
                    val infiniteTransition =
                        rememberInfiniteTransition(label = "Request Status Progress")
                    val shimmerTranslateX by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ), label = "Progress"
                    )
                    // Shimmer Effect
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .fillMaxWidth(fraction = state.currentProgress + 0.015f)
                            .clip(RoundedCornerShape(20.dp))
                            .graphicsLayer(translationX = shimmerTranslateX * 700f)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.6f),
                                        Color.Transparent
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(
                                        100f,
                                        0f
                                    )
                                ),
                                shape = RoundedCornerShape(40.dp)
                            )
                            .testTag(REQUEST_STATUS_PROGRESS_TEST_TAG)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box {
                LoginInProgressText(
                    stringId = state.currentStatusText,
                    progress = state.requestStatusProgress,
                    modifier = Modifier
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                        )
                        .testTag(CONNECTING_TO_SERVER_TAG)
                )
                // White-space to prevent jumping when visibility animates
                Text(
                    text = " ",
                    style = MaterialTheme.typography.subtitle2,
                    minLines = 2
                )
            }
            Spacer(modifier = Modifier.height(if (isInLandscape) 0.dp else 20.dp))
        }
    }
}

/**
 * Composable to show current status text with a fade in/out animation.
 */
@Composable
private fun LoginInProgressText(
    @StringRes stringId: Int,
    progress: Long = -1L,
    textChangeDuration: Long = 200,
    modifier: Modifier,
) {
    val isInPreview = LocalInspectionMode.current // To avoid text being hidden in previews
    var visible by rememberSaveable { mutableStateOf(isInPreview) }
    var currentTextId by rememberSaveable { mutableIntStateOf(stringId) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Text(
            text = if (progress > -1L) {
                stringResource(SharedRes.string.login_completing_operation, (progress / 10).toInt())
            } else {
                stringResource(id = currentTextId)
            },
            style = MaterialTheme.typography.subtitle2,
            modifier = modifier,
            textAlign = TextAlign.Center,
            minLines = 2
        )
    }

    LaunchedEffect(stringId) {
        visible = false
        delay(textChangeDuration)
        currentTextId = stringId
        visible = true
    }
}

@Composable
private fun TwoFactorAuthentication(
    state: LoginState,
    paddingValues: PaddingValues,
    on2FAPinChanged: (String, Int) -> Unit,
    on2FAChanged: (String) -> Unit,
    onLostAuthenticatorDevice: () -> Unit,
    onFirstTime2FAConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(
    modifier = modifier
        .fillMaxWidth()
        .padding(paddingValues)
) {
    val scrollState = rememberScrollState()
    val isChecking2FA = state.multiFactorAuthState == MultiFactorAuthState.Checking
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isError = state.multiFactorAuthState == MultiFactorAuthState.Failed
        Text(
            text = stringResource(id = R.string.explain_confirm_2fa),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 40.dp)
                .testTag(ENTER_AUTHENTICATION_CODE_TAG),
            style = MaterialTheme.typography.subtitle1.copy(color = MaterialTheme.colors.textColorSecondary),
            textAlign = TextAlign.Center
        )
        TwoFactorAuthenticationField(
            twoFAPin = state.twoFAPin,
            isError = isError,
            on2FAPinChanged = on2FAPinChanged,
            on2FAChanged = on2FAChanged,
            requestFocus = state.isFirstTime2FA,
            onRequestFocusConsumed = onFirstTime2FAConsumed
        )
        if (isError) {
            Text(
                text = stringResource(id = R.string.pin_error_2fa),
                modifier = Modifier
                    .padding(start = 10.dp, top = 18.dp, end = 10.dp)
                    .testTag(INVALID_CODE_TAG),
                style = MaterialTheme.typography.caption.copy(color = MaterialTheme.colors.error)
            )
        }
        TextMegaButton(
            textId = R.string.lost_your_authenticator_device,
            onClick = onLostAuthenticatorDevice,
            modifier = Modifier
                .padding(top = if (isError) 0.dp else 29.dp, bottom = 40.dp)
                .testTag(LOST_AUTHENTICATION_CODE_TAG)
        )
    }

    if (isChecking2FA) {
        MegaCircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.Center)
                .testTag(TWO_FA_PROGRESS_TEST_TAG)
        )
    }
}


@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "DarkEmptyLoginViewPreview")
@Composable
private fun EmptyLoginViewPreview() {
    OriginalTempTheme(isDark = isSystemInDarkTheme()) {
        var state by remember { mutableStateOf(LoginState(isLoginRequired = true)) }

        RequireLogin(
            state = state,
            onEmailChanged = {
                state = state.copy(accountSession = AccountSession(email = it))
            },
            onPasswordChanged = { state = state.copy(password = it) },
            onLoginClicked = {},
            onForgotPassword = {},
            onCreateAccount = {},
            paddingValues = PaddingValues(),
            onChangeApiServer = {},
            onReportIssue = {},
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "DarkLoginViewPreview")
@Composable
private fun LoginViewPreview(
    @PreviewParameter(LoginStateProvider::class) state: LoginState,
) {
    OriginalTempTheme(isDark = isSystemInDarkTheme()) {
        LoginView(
            state = state,
            onEmailChanged = {},
            onPasswordChanged = {},
            onLoginClicked = {},
            onForgotPassword = {},
            onCreateAccount = {},
            onSnackbarMessageConsumed = {},
            on2FAPinChanged = { _, _ -> },
            on2FAChanged = {},
            onLostAuthenticatorDevice = {},
            onBackPressed = {},
            onFirstTime2FAConsumed = {},
            onReportIssue = {},
        )
    }
}

@Preview
@Preview(
    uiMode = Configuration.ORIENTATION_LANDSCAPE,
    heightDp = 360,
    widthDp = 800,
    name = "LandscapeLoginViewPreview"
)
@Composable
private fun LandscapeLoginViewPreview(
    @PreviewParameter(LoginStateProvider::class) state: LoginState,
) {
    OriginalTempTheme(isDark = isSystemInDarkTheme()) {
        LoginView(
            state = state,
            onEmailChanged = {},
            onPasswordChanged = {},
            onLoginClicked = {},
            onForgotPassword = {},
            onCreateAccount = {},
            onSnackbarMessageConsumed = {},
            on2FAPinChanged = { _, _ -> },
            on2FAChanged = {},
            onLostAuthenticatorDevice = {},
            onBackPressed = {},
            onFirstTime2FAConsumed = {},
            onReportIssue = {},
        )
    }
}

/**
 * LoginState parameter provider for compose previews.
 */
internal class LoginStateProvider : PreviewParameterProvider<LoginState> {
    override val values = listOf(
        LoginState(
            isLoginRequired = true,
            accountSession = AccountSession(email = "email@email.es"),
            password = "Password",
            isLocalLogoutInProgress = true
        ),
        LoginState(
            isLoginRequired = true,
            accountSession = AccountSession(email = ""),
            emailError = LoginError.EmptyEmail,
            password = "",
            passwordError = LoginError.EmptyPassword
        ),
        LoginState(
            isLoginInProgress = true,
        ),
        LoginState(
            isLoginInProgress = true,
            requestStatusProgress = 200
        ),
        LoginState(
            isLoginInProgress = true,
            requestStatusProgress = 700
        ),
        LoginState(
            fetchNodesUpdate = FetchNodesUpdate(
                progress = Progress(0.5F),
                temporaryError = TemporaryWaitingError.ConnectivityIssues
            ),
        ),
        LoginState(
            is2FARequired = true,
            twoFAPin = listOf("1", "2", "", "", "", "")
        ),
        LoginState(
            multiFactorAuthState = MultiFactorAuthState.Failed,
            twoFAPin = listOf("1", "2", "3", "4", "5", "6")
        ),
        LoginState(
            multiFactorAuthState = MultiFactorAuthState.Checking,
            twoFAPin = listOf("1", "2", "3", "4", "5", "6")
        ),
    ).asSequence()
}

internal const val MEGA_LOGO_TEST_TAG = "MEGA_LOGO"
internal const val REQUEST_STATUS_PROGRESS_TEST_TAG = "login_in_progress:request_status_progress"
internal const val FETCH_NODES_PROGRESS_TEST_TAG = "FETCH_NODES_PROGRESS"
internal const val LOGIN_PROGRESS_TEST_TAG = "LOGIN_PROGRESS"
internal const val TWO_FA_PROGRESS_TEST_TAG = "TWO_FA_PROGRESS"
internal const val CHECKING_VALIDATION_TAG =
    "login_in_progress:login_in_progress_text_checking_validation_link"
internal const val CONNECTING_TO_SERVER_TAG =
    "login_in_progress:login_in_progress_text_connecting_to_the_server"
internal const val UPDATING_FILE_LIST_TAG =
    "login_in_progress:login_in_progress_text_updating_file_list"
internal const val PREPARING_FILE_LIST_TAG =
    "login_in_progress:login_in_progress_text_preparing_file_list"
internal const val TEMPORARY_ERROR_TAG =
    "login_in_progress:login_in_progress_text_temporary_error"
internal const val LOGIN_TO_MEGA_TAG = "require_login:text_login_to_mega"
internal const val TEXT_FIELD_LABEL_TAG =
    "require_login:label_text_field_displaying_the_email_address_label"
internal const val PASSWORD_TEXT_FIELD_TAG =
    "require_login:password_text_field_displaying_the_password"
internal const val FORGOT_PASSWORD_TAG = "require_login:text_mega_button_forgot_password"
internal const val LOGIN_BUTTON_TAG = "require_login:raised_default_mega_button_login"
internal const val CANCEL_LOGIN_PROGRESS_TAG =
    "require_login:mega_circular_progress_indicator_cancel_login_progress"
internal const val CANCELLING_LOGIN_TAG = "require_login:text_cancelling_login"
internal const val TROUBLE_LOGIN_TAG = "require_login:text_trouble_logging_in"
internal const val REPORT_ISSUE_TAG = "require_login:text_report_issue"
internal const val NEW_TO_MEGA_TAG = "require_login:text_new_to_mega"
internal const val CREATE_ACCOUNT_TAG = "require_login:text_mega_button_create_account"
internal const val ENTER_AUTHENTICATION_CODE_TAG =
    "two_factor_authentication:text_enter_authentication_code"
internal const val INVALID_CODE_TAG =
    "two_factor_authentication:text_invalid_code"
internal const val LOST_AUTHENTICATION_CODE_TAG =
    "two_factor_authentication:text_mega_button_lost_authentication_code"

private const val LONG_PRESS_DELAY = 5000L

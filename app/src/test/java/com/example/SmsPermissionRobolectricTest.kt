package com.example

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.SmsPermissionController
import com.example.ui.components.SmsPermissionDialogMode
import com.example.ui.components.SmsPermissionDialogs
import com.example.ui.screens.SmsInboxScreen
import com.example.ui.theme.FinPulseTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmsPermissionRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun firstScanTapShowsPrivacyDisclosureAndMarksRequested() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.READ_SMS)

        var markedRequested = false
        var scanCalled = false

        composeRule.setContent {
            FinPulseTheme {
                var requested by remember { mutableStateOf(false) }
                SmsInboxScreen(
                    messages = emptyList(),
                    customFolders = emptyList(),
                    hasRequestedSmsPermission = requested,
                    onMarkSmsPermissionRequested = {
                        markedRequested = true
                        requested = true
                    },
                    onPermissionDeniedMessage = {},
                    onScanDeviceSms = { scanCalled = true },
                    onImportManualSms = { _, _ -> },
                    onCorrectFolder = { _, _, _ -> },
                    onMarkRead = {},
                    onDeleteSms = {},
                    onCreateCustomFolder = { _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("sms_permission_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("scan_sms_button").performClick()

        composeRule.onNodeWithText("On-Device SMS Access").assertIsDisplayed()
        composeRule.onNodeWithTag("sms_disclosure_allow_button").performClick()

        assertTrue(markedRequested)
        assertEquals(false, scanCalled)
    }

    @Test
    fun permanentlyDeniedStateShowsSettingsDialogAndManualFallback() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.READ_SMS)

        composeRule.setContent {
            FinPulseTheme {
                SmsInboxScreen(
                    messages = emptyList(),
                    customFolders = emptyList(),
                    hasRequestedSmsPermission = true,
                    onMarkSmsPermissionRequested = {},
                    onPermissionDeniedMessage = {},
                    onScanDeviceSms = {},
                    onImportManualSms = { _, _ -> },
                    onCorrectFolder = { _, _, _ -> },
                    onMarkRead = {},
                    onDeleteSms = {},
                    onCreateCustomFolder = { _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("banner_open_settings_button").assertIsDisplayed()
        composeRule.onNodeWithTag("scan_sms_button").performClick()

        composeRule.onNodeWithText("Enable SMS in App Settings").assertIsDisplayed()
        composeRule.onNodeWithTag("sms_open_settings_button").assertIsDisplayed()
        composeRule.onNodeWithTag("sms_settings_manual_button").performClick()

        composeRule.onNodeWithTag("manual_sms_body_input").assertIsDisplayed()
    }

    @Test
    fun grantedPermissionHidesBannerAndTriggersScanDirectly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_SMS)

        var scanCount = 0

        composeRule.setContent {
            FinPulseTheme {
                SmsInboxScreen(
                    messages = emptyList(),
                    customFolders = emptyList(),
                    hasRequestedSmsPermission = true,
                    onMarkSmsPermissionRequested = {},
                    onPermissionDeniedMessage = {},
                    onScanDeviceSms = { scanCount++ },
                    onImportManualSms = { _, _ -> },
                    onCorrectFolder = { _, _, _ -> },
                    onMarkRead = {},
                    onDeleteSms = {},
                    onCreateCustomFolder = { _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("sms_permission_banner").assertDoesNotExist()
        composeRule.onNodeWithTag("scan_sms_button").performClick()

        assertEquals(1, scanCount)
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Test
    fun shouldShowRationaleTriggersRationaleDialogAndFallback() {
        var launchCount = 0
        var markedCount = 0
        var fallbackCount = 0

        val fakePermissionState = object : PermissionState {
            override val permission: String = Manifest.permission.READ_SMS
            override val status: PermissionStatus = PermissionStatus.Denied(shouldShowRationale = true)
            override fun launchPermissionRequest() {
                launchCount++
            }
        }

        lateinit var controller: SmsPermissionController
        composeRule.setContent {
            FinPulseTheme {
                val permHolder = remember { mutableStateOf<PermissionState?>(fakePermissionState) }
                val modeState = remember { mutableStateOf(SmsPermissionDialogMode.NONE) }
                val pendingState = remember { mutableStateOf(false) }
                val hasRequestedState = remember { mutableStateOf(true) }
                val markState = remember { mutableStateOf<() -> Unit>({ markedCount++ }) }
                val grantedState = remember { mutableStateOf<() -> Unit>({}) }

                controller = remember {
                    SmsPermissionController(
                        permissionStateHolder = permHolder,
                        dialogModeState = modeState,
                        pendingCallbackFromPrompt = pendingState,
                        hasRequestedBeforeState = hasRequestedState,
                        onMarkRequestedState = markState,
                        onPermissionGrantedState = grantedState
                    )
                }

                SmsPermissionDialogs(
                    controller = controller,
                    onFallbackToManualImport = { fallbackCount++ }
                )
            }
        }

        composeRule.runOnIdle { controller.requestOrScan() }
        composeRule.onNodeWithText("Why FinPulse Needs SMS Access").assertIsDisplayed()
        composeRule.onNodeWithTag("sms_rationale_grant_button").performClick()

        assertEquals(1, markedCount)
        assertEquals(1, launchCount)

        composeRule.runOnIdle { controller.requestOrScan() }
        composeRule.onNodeWithTag("sms_rationale_manual_button").performClick()
        assertEquals(1, fallbackCount)
    }
}

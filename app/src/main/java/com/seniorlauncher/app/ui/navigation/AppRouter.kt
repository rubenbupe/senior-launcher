package com.seniorlauncher.app.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.seniorlauncher.app.MINI_APP_CAMERA_ID
import com.seniorlauncher.app.MINI_APP_FLASHLIGHT_ID
import com.seniorlauncher.app.MINI_APP_GALLERY_ID
import com.seniorlauncher.app.MINI_APP_PHONE_ID
import com.seniorlauncher.app.MINI_APP_SMS_ID
import com.seniorlauncher.app.data.preferences.AppSettingsSnapshot
import com.seniorlauncher.app.service.AppsService
import com.seniorlauncher.app.service.AppsService.buildOrderedHomeApps
import com.seniorlauncher.app.service.PhoneService
import com.seniorlauncher.app.service.WhatsAppService
import com.seniorlauncher.app.service.sms.SmsService
import com.seniorlauncher.app.ui.screens.camera.InternalCameraCaptureScreen
import com.seniorlauncher.app.ui.screens.camera.InternalCameraMode
import com.seniorlauncher.app.ui.screens.camera.InternalCameraModePickerScreen
import com.seniorlauncher.app.ui.screens.flashlight.FlashlightScreen
import com.seniorlauncher.app.ui.screens.gallery.GalleryDetailScreen
import com.seniorlauncher.app.ui.screens.gallery.GalleryMediaItem
import com.seniorlauncher.app.ui.screens.gallery.GalleryScreen
import com.seniorlauncher.app.ui.screens.home.HomeScreen
import com.seniorlauncher.app.ui.screens.home.HomeMainViewModel
import com.seniorlauncher.app.ui.screens.home.PhoneViewModel
import com.seniorlauncher.app.ui.screens.home.PinDialog
import com.seniorlauncher.app.ui.screens.home.SettingsUiState
import com.seniorlauncher.app.ui.screens.home.SettingsViewModel
import com.seniorlauncher.app.ui.screens.home.SmsViewModel
import com.seniorlauncher.app.ui.screens.phone.ContactDetailScreen
import com.seniorlauncher.app.ui.screens.phone.ContactListScreen
import com.seniorlauncher.app.ui.screens.phone.DialerScreen
import com.seniorlauncher.app.ui.screens.settings.AllowedAppsSettingsScreen
import com.seniorlauncher.app.ui.screens.settings.SettingsScreen
import com.seniorlauncher.app.ui.screens.sms.SmsListScreen
import com.seniorlauncher.app.ui.screens.sms.SmsThreadScreen
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object PhoneHomeRoute
@Serializable data class ContactDetailRoute(val contactId: Long)
@Serializable object SettingsRoute
@Serializable object AllowedAppsRoute
@Serializable object CameraModeRoute
@Serializable data class CameraCaptureRoute(val mode: InternalCameraMode)
@Serializable object GalleryTimelineRoute
@Serializable data class GalleryDetailRoute(
    val id: Long,
    val uri: String,
    val isVideo: Boolean,
    val dateTakenMillis: Long
)
@Serializable object FlashlightRoute
@Serializable data class DialerRoute(val initialNumber: String = "")
@Serializable object SmsListRoute
@Serializable data class SmsThreadRoute(val threadId: Long, val address: String)

@Composable
fun AppRouter(
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsVm: SettingsViewModel = viewModel()

    val settingsState by settingsVm.uiState.collectAsState()

    val navController = rememberNavController()
    var pendingCallPhone by rememberSaveable { mutableStateOf<String?>(null) }
    var showPinDialog by rememberSaveable { mutableStateOf(false) }


    val requestCallPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val phone = pendingCallPhone
        if (granted && phone != null) {
            PhoneService.call(context, phone)
            pendingCallPhone = null
            navController.navigate(PhoneHomeRoute)
        }
    }

    fun placeCallWithPermission(rawPhone: String, onSuccess: (() -> Unit)? = null) {
        if (!hasPermission(context, Manifest.permission.CALL_PHONE)) {
            pendingCallPhone = rawPhone
            requestCallPermission.launch(Manifest.permission.CALL_PHONE)
        } else {
            PhoneService.call(context, rawPhone)
            onSuccess?.invoke()
        }
    }

    val requestSmsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) navController.navigate(SmsListRoute)
    }

    val requestBackendPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val requestDefaultSmsAppRole = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* SmsViewModel observer picks up changes automatically */ }


    LaunchedEffect(Unit) {
        val permissionsToRequest = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.POST_NOTIFICATIONS
        ).filterNot { hasPermission(context, it) }
        if (permissionsToRequest.isNotEmpty()) {
            requestBackendPermissions.launch(permissionsToRequest.toTypedArray())
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) settingsVm.reloadAvailableApps()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }


    if (showPinDialog) {
        PinDialog(
            onDismiss = { showPinDialog = false },
            onCorrect = {
                showPinDialog = false
                navController.navigate(SettingsRoute)
            }
        )
    }

    HomeNavGraph(
        navController = navController,
        settingsVm = settingsVm,
        rootSettingsState = settingsState,
        useNavAnimations = settingsState.settings.navigationAnimationsEnabled,
        onSettingsLongPress = { showPinDialog = true },
        requestSmsPermission = { requestSmsPermission.launch(Manifest.permission.READ_SMS) },
        requestDefaultSmsRole = {
            val intent = SmsService.createDefaultSmsRoleRequestIntent(context)
            if (intent != null) requestDefaultSmsAppRole.launch(intent)
        },
        placeCallWithPermission = ::placeCallWithPermission,
        openInstalledApp = { packageName -> AppsService.openInstalledApp(context, packageName) }
    )
}

@Composable
private fun HomeNavGraph(
    navController: NavHostController,
    settingsVm: SettingsViewModel,
    rootSettingsState: SettingsUiState,
    useNavAnimations: Boolean,
    onSettingsLongPress: () -> Unit,
    requestSmsPermission: () -> Unit,
    requestDefaultSmsRole: () -> Unit,
    placeCallWithPermission: (String, (() -> Unit)?) -> Unit,
    openInstalledApp: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            if (!useNavAnimations) {
                EnterTransition.None
            } else {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    ),
                    initialOffset = { it }
                ) + fadeIn(tween(300, easing = FastOutSlowInEasing))
            }
        },
        exitTransition = {
            if (!useNavAnimations) {
                ExitTransition.None
            } else {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    targetOffset = { it / 4 }
                ) + fadeOut(tween(300))
            }
        },
        popEnterTransition = {
            if (!useNavAnimations) {
                EnterTransition.None
            } else {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    initialOffset = { it / 4 }
                ) + fadeIn(tween(300))
            }
        },
        popExitTransition = {
            if (!useNavAnimations) {
                ExitTransition.None
            } else {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(300))
            }
        }
    ) {
        composable<HomeRoute> {
            val mainVm: HomeMainViewModel = viewModel()
            val phoneVm: PhoneViewModel = viewModel()
            val smsVm: SmsViewModel = viewModel()
            val context = LocalContext.current

            val mainState by mainVm.uiState.collectAsState()
            val phoneState by phoneVm.uiState.collectAsState()
            val smsState by smsVm.uiState.collectAsState()

            HomeScreen(
                userName = rootSettingsState.settings.userName.ifBlank { "Herminia" },
                greeting = mainState.greeting,
                showSosButton = rootSettingsState.settings.showSosButton,
                apps = buildOrderedHomeApps(
                    availableAppOptions = rootSettingsState.availableAppOptions,
                    selectedIds = rootSettingsState.settings.selectedHomeAppIds
                ),
                isAppsReady = rootSettingsState.homeAppsReady,
                onSettingsLongPress = onSettingsLongPress,
                onSosClick = {
                    val phone = rootSettingsState.settings.sosPhoneNumber.ifBlank { "112" }
                    placeCallWithPermission(phone, null)
                },
                onAppClick = { app ->
                    when (app.id) {
                        MINI_APP_PHONE_ID -> navController.navigate(PhoneHomeRoute)
                        MINI_APP_CAMERA_ID -> navController.navigate(CameraModeRoute)
                        MINI_APP_GALLERY_ID -> navController.navigate(GalleryTimelineRoute)
                        MINI_APP_SMS_ID -> {
                            if (!hasPermission(context, Manifest.permission.READ_SMS)) {
                                requestSmsPermission()
                            } else {
                                navController.navigate(SmsListRoute)
                            }
                        }
                        MINI_APP_FLASHLIGHT_ID -> navController.navigate(FlashlightRoute)
                        else -> app.packageName?.let(openInstalledApp)
                    }
                },
                unreadSmsCount = smsState.unreadSmsCount,
                totalMissedCalls = phoneState.totalMissedCalls
            )
        }

        composable<PhoneHomeRoute> {
            val phoneVm: PhoneViewModel = viewModel()
            val phoneState by phoneVm.uiState.collectAsState()

            ContactListScreen(
                onBack = { navController.popBackStack() },
                onContactClick = { contact ->
                    navController.navigate(ContactDetailRoute(contact.id))
                },
                onDialerClick = {
                    navController.navigate(DialerRoute())
                },
                allContacts = phoneState.allContacts,
                favoriteContactIds = phoneState.favoriteContacts.map { it.id }.toSet()
            )
        }

        composable<ContactDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ContactDetailRoute>()
            val phoneVm: PhoneViewModel = viewModel()
            val phoneState by phoneVm.uiState.collectAsState()
            val settingsState by settingsVm.uiState.collectAsState()
            val contact = phoneState.allContacts.firstOrNull { it.id == route.contactId }

            LaunchedEffect(route.contactId) {
                if (contact == null) phoneVm.loadContacts()
            }

            if (contact == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cargando contacto...")
                }
            } else {
                LaunchedEffect(contact.id) {
                    phoneVm.clearMissedCallsForContact(contact)
                }
                val context = LocalContext.current
                ContactDetailScreen(
                    contact = contact,
                    useWhatsApp = settingsState.settings.useWhatsApp,
                    onBack = { navController.popBackStack() },
                    onCall = {
                        val phone = PhoneService.sanitizePhone(contact.phone.orEmpty())
                        if (phone.isNotBlank()) placeCallWithPermission(phone) { }
                    },
                    onWhatsApp = { WhatsAppService.openWhatsAppToContact(context, contact.phone) }
                )
            }
        }

        composable<SettingsRoute> {
            val settingsState by settingsVm.uiState.collectAsState()

            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAllowedApps = {
                    settingsVm.reloadAvailableApps()
                    navController.navigate(AllowedAppsRoute)
                },
                initialUserName = settingsState.settings.userName.ifBlank { "Herminia" },
                initialUseWhatsApp = settingsState.settings.useWhatsApp,
                initialProtectDndMode = settingsState.settings.protectDndMode,
                initialLockDeviceVolume = settingsState.settings.lockDeviceVolume,
                initialLockedVolumePercent = settingsState.settings.lockedVolumePercent,
                initialBackendSyncEnabled = settingsState.settings.backendSyncEnabled,
                initialBackendServerUrl = settingsState.settings.backendServerUrl,
                initialBackendDeviceId = settingsState.settings.backendDeviceId,
                initialBackendApiToken = settingsState.settings.backendApiToken,
                initialNavigationAnimationsEnabled = settingsState.settings.navigationAnimationsEnabled,
                initialShowSosButton = settingsState.settings.showSosButton,
                initialSosPhoneNumber = settingsState.settings.sosPhoneNumber,
                availableAppOptions = settingsState.availableAppOptions,
                initialEnabledAppIds = settingsState.settings.selectedHomeAppIds.toSet(),
                onSave = { name, useWa, protectDnd, lockVol, volPct, syncEnabled, serverUrl, deviceId, apiToken, animEnabled, sosEnabled, sosPhone, enabledIds ->
                    val orderedSelected = settingsState.settings.selectedHomeAppIds
                        .filter { it in enabledIds } +
                        enabledIds.filterNot { it in settingsState.settings.selectedHomeAppIds }
                    settingsVm.saveSettings(
                        AppSettingsSnapshot(
                            userName = name,
                            useWhatsApp = useWa,
                            protectDndMode = protectDnd,
                            lockDeviceVolume = lockVol,
                            lockedVolumePercent = volPct,
                            backendSyncEnabled = syncEnabled,
                            backendServerUrl = serverUrl,
                            backendDeviceId = deviceId,
                            backendApiToken = apiToken,
                            navigationAnimationsEnabled = animEnabled,
                            showSosButton = sosEnabled,
                            sosPhoneNumber = sosPhone,
                            selectedHomeAppIds = orderedSelected
                        )
                    )
                }
            )
        }

        composable<AllowedAppsRoute> {
            val settingsState by settingsVm.uiState.collectAsState()

            AllowedAppsSettingsScreen(
                onBack = { navController.popBackStack() },
                availableAppOptions = settingsState.availableAppOptions,
                initialSelectedAppIds = settingsState.settings.selectedHomeAppIds,
                onSelectedAppIdsChange = { selected ->
                    settingsVm.saveSettings(settingsState.settings.copy(selectedHomeAppIds = selected))
                }
            )
        }

        composable<DialerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<DialerRoute>()
            DialerScreen(
                initialNumber = route.initialNumber,
                onBack = { navController.popBackStack() },
                onCallRequested = { phone ->
                    placeCallWithPermission(phone) { navController.popBackStack() }
                }
            )
        }

        composable<SmsListRoute> {
            val smsVm: SmsViewModel = viewModel()
            val smsState by smsVm.uiState.collectAsState()

            LaunchedEffect(Unit) { smsVm.onEnterSmsList() }
            SmsListScreen(
                conversations = smsState.smsConversations,
                isLoading = smsState.isSmsListLoading,
                showDefaultAppDialog = smsState.showSmsDefaultDialog,
                onDismissDefaultDialog = { smsVm.setShowSmsDefaultDialog(false) },
                onRequestDefaultApp = requestDefaultSmsRole,
                onBack = { navController.popBackStack() },
                onThreadClick = { conv ->
                    navController.navigate(SmsThreadRoute(conv.threadId, conv.address))
                },
                onMarkAllRead = { smsVm.markAllSmsAsRead() },
                onDeleteConversation = { threadId -> smsVm.deleteSmsConversation(threadId) }
            )
        }

        composable<SmsThreadRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SmsThreadRoute>()
            val smsVm: SmsViewModel = viewModel()
            val smsState by smsVm.uiState.collectAsState()

            LaunchedEffect(route.threadId, route.address) {
                smsVm.selectSmsThread(route.threadId, route.address)
                smsVm.onEnterSmsThread()
            }
            SmsThreadScreen(
                title = route.address.ifBlank { "Conversación" },
                messages = smsState.smsMessages,
                isLoading = smsState.isSmsThreadLoading,
                onBack = { navController.popBackStack() },
                onDeleteConversation = {
                    smsVm.deleteSmsConversation(route.threadId)
                    navController.popBackStack()
                }
            )
        }

        composable<CameraModeRoute> {
            InternalCameraModePickerScreen(
                onBack = { navController.popBackStack() },
                onModeSelected = { mode ->
                    navController.navigate(CameraCaptureRoute(mode))
                }
            )
        }

        composable<CameraCaptureRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<CameraCaptureRoute>()
            InternalCameraCaptureScreen(
                mode = route.mode,
                onBack = { navController.popBackStack() }
            )
        }

        composable<GalleryTimelineRoute> {
            GalleryScreen(
                onBack = { navController.popBackStack() },
                onItemSelected = { item ->
                    navController.navigate(
                        GalleryDetailRoute(
                            id = item.id,
                            uri = item.uri.toString(),
                            isVideo = item.isVideo,
                            dateTakenMillis = item.dateTakenMillis
                        )
                    )
                }
            )
        }

        composable<GalleryDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GalleryDetailRoute>()
            val item = remember(route) {
                GalleryMediaItem(
                    id = route.id,
                    uri = Uri.parse(route.uri),
                    isVideo = route.isVideo,
                    dateTakenMillis = route.dateTakenMillis
                )
            }
            GalleryDetailScreen(
                item = item,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() }
            )
        }

        composable<FlashlightRoute> {
            FlashlightScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun hasPermission(context: android.content.Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

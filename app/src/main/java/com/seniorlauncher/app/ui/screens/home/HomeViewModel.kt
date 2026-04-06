package com.seniorlauncher.app.ui.screens.home

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seniorlauncher.app.AllowedAppOption
import com.seniorlauncher.app.service.AppsService
import com.seniorlauncher.app.data.model.Contact
import com.seniorlauncher.app.data.model.SmsConversation
import com.seniorlauncher.app.data.model.SmsItem
import com.seniorlauncher.app.data.preferences.AppSettingsService
import com.seniorlauncher.app.data.preferences.AppSettingsSnapshot
import com.seniorlauncher.app.data.repository.ContactsRepository
import com.seniorlauncher.app.data.repository.SmsRepository
import com.seniorlauncher.app.service.sms.SmsService
import com.seniorlauncher.app.service.sync.SyncService
import com.seniorlauncher.app.ui.screens.camera.InternalCameraMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class HomeMainUiState(val greeting: String = "")

class HomeMainViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(HomeMainUiState())
    val uiState: StateFlow<HomeMainUiState> = _uiState.asStateFlow()

    init { initGreeting() }

    fun initGreeting() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Buenos días,"
            in 12..17 -> "Buenas tardes,"
            else -> "Buenas noches,"
        }
        _uiState.update { it.copy(greeting = greeting) }
    }
}


data class SettingsUiState(
    val settings: AppSettingsSnapshot = AppSettingsSnapshot(
        userName = "",
        useWhatsApp = true,
        protectDndMode = true,
        lockDeviceVolume = true,
        lockedVolumePercent = 70,
        backendSyncEnabled = false,
        backendServerUrl = "",
        backendDeviceId = "",
        backendApiToken = "",
        navigationAnimationsEnabled = true,
        showSosButton = true,
        sosPhoneNumber = "112",
        selectedHomeAppIds = emptyList()
    ),
    val availableAppOptions: List<AllowedAppOption> = emptyList(),
    val homeAppsReady: Boolean = false,
    val showPinDialog: Boolean = false
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var settingsObserverJob: Job? = null
    private var lastSyncSignature: String? = null

    private val volumeAndDndObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) = enforceLockedVolume()
    }

    private val systemEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            enforceDndProtection()
            enforceLockedVolume()
        }
    }

    init {
        observeSettings()
        reloadAvailableApps()
        setupSystemEnforcement()
    }

    private fun observeSettings() {
        settingsObserverJob?.cancel()
        settingsObserverJob = viewModelScope.launch {
            AppSettingsService.observe(context).collect { snapshot ->
                _uiState.update { it.copy(settings = snapshot) }
                val syncSignature = listOf(
                    snapshot.backendSyncEnabled.toString(),
                    snapshot.backendServerUrl,
                    snapshot.backendDeviceId,
                    snapshot.backendApiToken
                ).joinToString("|")

                val previous = lastSyncSignature
                lastSyncSignature = syncSignature

                if (previous == null) {
                    SyncService.startIfConfigured(context)
                } else if (previous != syncSignature) {
                    SyncService.restartIfConfigured(context)
                }

                enforceDndProtection()
                enforceLockedVolume()
            }
        }
    }

    private fun setupSystemEnforcement() {
        val filter = IntentFilter().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            }
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(systemEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(systemEventReceiver, filter)
        }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI, true, volumeAndDndObserver
        )
        enforceDndProtection()
        enforceLockedVolume()
    }

    fun loadSettings(reloadApps: Boolean = true) {
        if (reloadApps) reloadAvailableApps()
    }

    fun saveSettings(snapshot: AppSettingsSnapshot) {
        viewModelScope.launch(Dispatchers.IO) {
            AppSettingsService.save(context, snapshot)
        }
    }

    fun reloadAvailableApps() {
        viewModelScope.launch {
            val apps = runCatching {
                withContext(Dispatchers.IO) { AppsService.getAvailableApps(context) }
            }.getOrDefault(emptyList())
            _uiState.update { it.copy(availableAppOptions = apps, homeAppsReady = true) }
        }
    }

    fun hasPermission(permission: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        } else true


    private fun enforceDndProtection() {
        if (!_uiState.value.settings.protectDndMode) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        }
    }

    private fun enforceLockedVolume() {
        val s = _uiState.value.settings
        if (!s.lockDeviceVolume) return
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((s.lockedVolumePercent.coerceIn(0, 100) / 100f) * maxVol).toInt().coerceIn(0, maxVol)
        runCatching {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            am.setStreamVolume(AudioManager.STREAM_RING, target, 0)
            am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        }
    }

    override fun onCleared() {
        super.onCleared()
        settingsObserverJob?.cancel()
        runCatching { context.unregisterReceiver(systemEventReceiver) }
        runCatching { context.contentResolver.unregisterContentObserver(volumeAndDndObserver) }
    }
}


data class PhoneUiState(
    val favoriteContacts: List<Contact> = emptyList(),
    val allContacts: List<Contact> = emptyList(),
    val missedCallsByContactId: Map<Long, Int> = emptyMap(),
    val totalMissedCalls: Int = 0,
    val pendingCallPhone: String? = null
)

class PhoneViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow(PhoneUiState())
    val uiState: StateFlow<PhoneUiState> = _uiState.asStateFlow()

    private var contactObserverRegistered = false
    private var callLogObserverRegistered = false
    private var pendingContactsReloadJob: Job? = null
    private var pendingMissedCallsReloadJob: Job? = null


    private val contactObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (!hasPermission(Manifest.permission.READ_CONTACTS)) return
            scheduleContactsReload()
        }
    }

    private val callLogObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return
            scheduleMissedCallsReload()
        }
    }

    private fun scheduleContactsReload() {
        pendingContactsReloadJob?.cancel()
        pendingContactsReloadJob = viewModelScope.launch {
            delay(250)
            loadContacts()
        }
    }

    private fun scheduleMissedCallsReload() {
        pendingMissedCallsReloadJob?.cancel()
        pendingMissedCallsReloadJob = viewModelScope.launch {
            delay(250)
            loadMissedCalls()
        }
    }

    init {
        ensureContactObserverRegistered()
        ensureCallLogObserverRegistered()
        if (hasPermission(Manifest.permission.READ_CONTACTS)) loadContacts()
        if (hasPermission(Manifest.permission.READ_CALL_LOG)) loadMissedCalls()
    }


    fun setPendingCallPhone(phone: String?) = _uiState.update { it.copy(pendingCallPhone = phone) }

    fun loadContacts() {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return
        ensureContactObserverRegistered()
        viewModelScope.launch {
            val (favs, all) = withContext(Dispatchers.IO) {
                ContactsRepository.loadAllAndFavorites(context)
            }
            _uiState.update { it.copy(favoriteContacts = favs, allContacts = all) }
            loadMissedCalls()
        }
    }

    fun loadMissedCalls() {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return
        ensureCallLogObserverRegistered()
        viewModelScope.launch {
            val contacts = _uiState.value.favoriteContacts
            val (byContact, total) = withContext(Dispatchers.IO) {
                ContactsRepository.loadMissedCallsByContact(context, contacts)
            }
            _uiState.update { it.copy(missedCallsByContactId = byContact, totalMissedCalls = total) }
        }
    }

    fun clearMissedCallsForContact(contact: Contact) {
        val badge = _uiState.value.missedCallsByContactId[contact.id] ?: 0
        _uiState.update {
            it.copy(
                missedCallsByContactId = it.missedCallsByContactId - contact.id,
                totalMissedCalls = (it.totalMissedCalls - badge).coerceAtLeast(0)
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            ContactsRepository.clearMissedCallsForContact(context, contact)
        }
    }

    fun setFavorite(contact: Contact, favorite: Boolean, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                ContactsRepository.setFavorite(context, contact.id, favorite)
            }
            if (ok) loadContacts()
            onDone(ok)
        }
    }

    fun deleteContact(contact: Contact, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                ContactsRepository.delete(context, contact.id)
            }
            if (ok) loadContacts()
            onDone(ok)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        } else true

    private fun ensureContactObserverRegistered() {
        if (contactObserverRegistered) return
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return
        runCatching {
            context.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true, contactObserver
            )
            context.contentResolver.registerContentObserver(
                ContactsContract.RawContacts.CONTENT_URI, true, contactObserver
            )
            contactObserverRegistered = true
        }
    }

    private fun ensureCallLogObserverRegistered() {
        if (callLogObserverRegistered) return
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return
        runCatching {
            context.contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI, true, callLogObserver
            )
            callLogObserverRegistered = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        pendingContactsReloadJob?.cancel()
        pendingMissedCallsReloadJob?.cancel()
        if (contactObserverRegistered) {
            runCatching { context.contentResolver.unregisterContentObserver(contactObserver) }
        }
        if (callLogObserverRegistered) {
            runCatching { context.contentResolver.unregisterContentObserver(callLogObserver) }
        }
    }
}


data class SmsUiState(
    val unreadSmsCount: Int = 0,
    val smsConversations: List<SmsConversation> = emptyList(),
    val smsMessages: List<SmsItem> = emptyList(),
    val isSmsListLoading: Boolean = false,
    val isSmsThreadLoading: Boolean = false,
    val showSmsDefaultDialog: Boolean = false,
    val selectedSmsThreadId: Long? = null,
    val selectedSmsAddress: String = ""
)

class SmsViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow(SmsUiState())
    val uiState: StateFlow<SmsUiState> = _uiState.asStateFlow()

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (!hasPermission(Manifest.permission.READ_SMS)) return
            viewModelScope.launch {
                refreshUnreadSmsCount()
                loadSmsConversations()
                val threadId = _uiState.value.selectedSmsThreadId
                if (threadId != null) loadSmsThread(threadId)
            }
        }
    }

    init {
        if (hasPermission(Manifest.permission.READ_SMS)) refreshUnreadSmsCount()
        context.contentResolver.registerContentObserver(
            Uri.parse("content://sms"), true, smsObserver
        )
    }

    fun onEnterSmsList() {
        if (!SmsService.canModify(context)) setShowSmsDefaultDialog(true)
        if (hasPermission(Manifest.permission.READ_SMS)) loadSmsConversations()
    }

    fun onEnterSmsThread() {
        val threadId = _uiState.value.selectedSmsThreadId ?: return
        if (hasPermission(Manifest.permission.READ_SMS)) loadSmsThread(threadId)
    }

    fun selectSmsThread(threadId: Long, address: String) {
        _uiState.update {
            it.copy(
                selectedSmsThreadId = threadId,
                selectedSmsAddress = address
            )
        }
    }

    fun setShowSmsDefaultDialog(show: Boolean) = _uiState.update { it.copy(showSmsDefaultDialog = show) }

    fun loadSmsConversations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSmsListLoading = it.smsConversations.isEmpty()) }
            val conversations = withContext(Dispatchers.IO) { SmsRepository.loadConversations(context) }
            val unread = withContext(Dispatchers.IO) { SmsRepository.getUnreadCount(context) }
            _uiState.update { it.copy(smsConversations = conversations, unreadSmsCount = unread, isSmsListLoading = false) }
        }
    }

    fun loadSmsThread(threadId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSmsThreadLoading = it.smsMessages.isEmpty()) }
            withContext(Dispatchers.IO) { SmsRepository.markThreadAsRead(context, threadId) }
            val messages = withContext(Dispatchers.IO) { SmsRepository.loadThreadMessages(context, threadId) }
            val conversations = withContext(Dispatchers.IO) { SmsRepository.loadConversations(context) }
            val unread = withContext(Dispatchers.IO) { SmsRepository.getUnreadCount(context) }
            _uiState.update {
                it.copy(smsMessages = messages, smsConversations = conversations, unreadSmsCount = unread, isSmsThreadLoading = false)
            }
        }
    }

    fun markAllSmsAsRead() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SmsRepository.markAllAsRead(context) }
            loadSmsConversations()
        }
    }

    fun deleteSmsConversation(threadId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SmsRepository.deleteConversation(context, threadId) }
            loadSmsConversations()
        }
    }

    fun refreshUnreadSmsCount() {
        viewModelScope.launch {
            val unread = withContext(Dispatchers.IO) { SmsRepository.getUnreadCount(context) }
            _uiState.update { it.copy(unreadSmsCount = unread) }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        } else true

    override fun onCleared() {
        super.onCleared()
        runCatching { context.contentResolver.unregisterContentObserver(smsObserver) }
    }
}

data class MediaUiState(
    val internalCameraMode: InternalCameraMode = InternalCameraMode.PHOTO
)

class MediaViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    fun setCameraMode(mode: InternalCameraMode) = _uiState.update { it.copy(internalCameraMode = mode) }
}

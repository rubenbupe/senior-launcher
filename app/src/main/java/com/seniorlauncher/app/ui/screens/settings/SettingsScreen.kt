package com.seniorlauncher.app.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seniorlauncher.app.AllowedAppOption
import com.seniorlauncher.app.data.preferences.checkPin
import com.seniorlauncher.app.data.preferences.setPin
import com.seniorlauncher.app.ui.components.AppBottomSecondaryButton
import com.seniorlauncher.app.ui.components.AppSubScreen
import com.seniorlauncher.app.ui.theme.HeaderColor
import java.util.Collections
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAllowedApps: () -> Unit,
    initialUserName: String,
    initialUseWhatsApp: Boolean,
    initialProtectDndMode: Boolean,
    initialLockDeviceVolume: Boolean,
    initialLockedVolumePercent: Int,
    initialBackendSyncEnabled: Boolean,
    initialBackendServerUrl: String,
    initialBackendDeviceId: String,
    initialBackendApiToken: String,
    initialNavigationAnimationsEnabled: Boolean,
    initialShowSosButton: Boolean,
    initialSosPhoneNumber: String,
    availableAppOptions: List<AllowedAppOption>,
    initialEnabledAppIds: Set<String>,
    onSave: (
        userName: String,
        useWhatsApp: Boolean,
        protectDndMode: Boolean,
        lockDeviceVolume: Boolean,
        lockedVolumePercent: Int,
        backendSyncEnabled: Boolean,
        backendServerUrl: String,
        backendDeviceId: String,
        backendApiToken: String,
        navigationAnimationsEnabled: Boolean,
        showSosButton: Boolean,
        sosPhoneNumber: String,
        enabledAppIds: Set<String>
    ) -> Unit
) {
    val context = LocalContext.current
    var userName by remember(initialUserName) { mutableStateOf(initialUserName) }
    var useWhatsApp by remember(initialUseWhatsApp) { mutableStateOf(initialUseWhatsApp) }
    var protectDndMode by remember(initialProtectDndMode) { mutableStateOf(initialProtectDndMode) }
    var lockDeviceVolume by remember(initialLockDeviceVolume) { mutableStateOf(initialLockDeviceVolume) }
    var lockedVolumePercent by remember(initialLockedVolumePercent) {
        mutableStateOf(initialLockedVolumePercent.coerceIn(0, 100))
    }
    var backendSyncEnabled by remember(initialBackendSyncEnabled) { mutableStateOf(initialBackendSyncEnabled) }
    var backendServerUrl by remember(initialBackendServerUrl) { mutableStateOf(initialBackendServerUrl) }
    var backendDeviceId by remember(initialBackendDeviceId) { mutableStateOf(initialBackendDeviceId) }
    var backendApiToken by remember(initialBackendApiToken) { mutableStateOf(initialBackendApiToken) }
    var navigationAnimationsEnabled by remember(initialNavigationAnimationsEnabled) {
        mutableStateOf(initialNavigationAnimationsEnabled)
    }
    var showSosButton by remember(initialShowSosButton) { mutableStateOf(initialShowSosButton) }
    var sosPhoneNumber by remember(initialSosPhoneNumber) {
        mutableStateOf(initialSosPhoneNumber.ifBlank { "112" })
    }
    var enabledAppIds by remember(initialEnabledAppIds) { mutableStateOf(initialEnabledAppIds) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    val totalSelectableApps = remember(availableAppOptions) { availableAppOptions.size }
    val sectionShape = RoundedCornerShape(20.dp)

    fun submitSettings() {
        onSave(
            userName,
            useWhatsApp,
            protectDndMode,
            lockDeviceVolume,
            lockedVolumePercent,
            backendSyncEnabled,
            backendServerUrl,
            backendDeviceId,
            backendApiToken,
            navigationAnimationsEnabled,
            showSosButton,
            sosPhoneNumber,
            enabledAppIds
        )
    }

    fun submitPinChange() {
        pinError = ""
        when {
            currentPin.length < 4 -> pinError = "Introduce el PIN actual"
            !checkPin(context, currentPin) -> pinError = "PIN actual incorrecto"
            newPin.length != 4 -> pinError = "El nuevo PIN debe tener 4 dígitos"
            newPin != confirmPin -> pinError = "Los PINs no coinciden"
            else -> {
                setPin(context, newPin)
                currentPin = ""
                newPin = ""
                confirmPin = ""
                pinError = ""
            }
        }
    }

    fun handleBack() {
        submitSettings()
        onBack()
    }

    BackHandler(onBack = ::handleBack)

    AppSubScreen(
        title = "Configuración",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = sectionShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Perfil",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Nombre de la persona",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BasicTextField(
                            value = userName,
                            onValueChange = { userName = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            textStyle = TextStyle(
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Normal
                            ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            cursorBrush = SolidColor(HeaderColor),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (userName.isEmpty()) {
                                    Text(
                                        text = "Ej: Herminia",
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                inner()
                            }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SettingsToggleRow(
                            title = "Usar WhatsApp",
                            subtitle = "Si está desactivado, no se mostrará el botón de WhatsApp.",
                            checked = useWhatsApp,
                            onCheckedChange = { useWhatsApp = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = sectionShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pantalla principal",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Gestiona qué apps aparecen en Inicio desde una subpantalla dedicada.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Activas: ${enabledAppIds.size} de $totalSelectableApps",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onOpenAllowedApps,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Abrir apps permitidas", color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsToggleRow(
                            title = "Animaciones al navegar",
                            subtitle = "Deslizar lateral entre subpantallas",
                            checked = navigationAnimationsEnabled,
                            onCheckedChange = { navigationAnimationsEnabled = it }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsToggleRow(
                            title = "Mostrar botón SOS",
                            subtitle = "Muestra un botón fijo en la parte superior de Inicio",
                            checked = showSosButton,
                            onCheckedChange = { showSosButton = it }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TextField(
                            value = sosPhoneNumber,
                            onValueChange = { sosPhoneNumber = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Número SOS") },
                            placeholder = { Text("112") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            enabled = showSosButton,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = sectionShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Sincronización backend",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Permite que el backend actualice estas configuraciones automáticamente.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsToggleRow(
                            title = "Habilitar sync de configuración",
                            subtitle = null,
                            checked = backendSyncEnabled,
                            onCheckedChange = { backendSyncEnabled = it }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TextField(
                            value = backendServerUrl,
                            onValueChange = { backendServerUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("URL servidor") },
                            placeholder = { Text("https://tu-servidor.com") },
                            singleLine = true,
                            enabled = backendSyncEnabled,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = backendDeviceId,
                            onValueChange = { backendDeviceId = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("ID del dispositivo") },
                            placeholder = { Text("device-01") },
                            singleLine = true,
                            enabled = backendSyncEnabled,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = backendApiToken,
                            onValueChange = { backendApiToken = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Token API") },
                            placeholder = { Text("Bearer token") },
                            singleLine = true,
                            enabled = backendSyncEnabled,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = sectionShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Protección",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Evita cambios accidentales en ajustes importantes.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsToggleRow(
                            title = "Desactivar automáticamente No molestar",
                            subtitle = null,
                            checked = protectDndMode,
                            onCheckedChange = { protectDndMode = it }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsToggleRow(
                            title = "Bloquear volumen del dispositivo",
                            subtitle = "Nivel fijado: ${lockedVolumePercent}%",
                            checked = lockDeviceVolume,
                            onCheckedChange = { lockDeviceVolume = it }
                        )
                        Slider(
                            value = lockedVolumePercent.toFloat(),
                            onValueChange = {
                                lockedVolumePercent = it.roundToInt().coerceIn(0, 100)
                            },
                            valueRange = 0f..100f,
                            enabled = lockDeviceVolume,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = sectionShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Seguridad",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "El PIN se usa para abrir la configuración (mantén pulsado el icono de la rueda).",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(
                            value = currentPin,
                            onValueChange = {
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                    currentPin = it; pinError = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("PIN actual") },
                            placeholder = { Text("PIN actual") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submitPinChange() }),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = newPin,
                            onValueChange = {
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) newPin = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nuevo PIN") },
                            placeholder = { Text("4 dígitos") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = confirmPin,
                            onValueChange = {
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Repetir nuevo PIN") },
                            placeholder = { Text("Repetir") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            isError = pinError.isNotEmpty(),
                            supportingText = {
                                if (pinError.isNotEmpty()) Text(
                                    pinError,
                                    color = Color.Red
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { submitPinChange() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cambiar PIN", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        bottomBar = {
            AppBottomSecondaryButton(
                text = "Atrás",
                icon = Icons.Default.ArrowBack,
                onClick = ::handleBack
            )
        }
    )
}

@Composable
fun AllowedAppsSettingsScreen(
    onBack: () -> Unit,
    availableAppOptions: List<AllowedAppOption>,
    initialSelectedAppIds: List<String>,
    onSelectedAppIdsChange: (List<String>) -> Unit
) {
    var selectedAppIds by remember(initialSelectedAppIds) {
        mutableStateOf(initialSelectedAppIds.distinct())
    }
    val miniAppOptions = remember(availableAppOptions) { availableAppOptions.filter { it.isMiniApp } }
    val installedAppOptions = remember(availableAppOptions) {
        availableAppOptions
            .filter { !it.isMiniApp }
            .sortedBy { it.label.lowercase() }
    }
    val availableIds = remember(availableAppOptions) { availableAppOptions.map { it.id }.toSet() }

    val orderedEnabledApps = remember(availableAppOptions, selectedAppIds) {
        val byId = availableAppOptions.associateBy { it.id }
        val ordered = mutableListOf<AllowedAppOption>()
        val seen = linkedSetOf<String>()

        selectedAppIds.forEach { id ->
            val app = byId[id] ?: return@forEach
            if (seen.add(id)) ordered.add(app)
        }

        ordered
    }

    val selectedIds = remember(orderedEnabledApps) { orderedEnabledApps.map { it.id }.toSet() }
    val selectableMiniApps = remember(miniAppOptions, selectedIds) {
        miniAppOptions.filter { !selectedIds.contains(it.id) }
    }
    val selectableInstalledApps = remember(installedAppOptions, selectedIds) {
        installedAppOptions.filter { !selectedIds.contains(it.id) }
    }

    fun applySelection(newOrderedIds: List<String>) {
        val normalized = newOrderedIds
            .filter { availableIds.contains(it) }
            .distinct()
        selectedAppIds = normalized
    }

    LaunchedEffect (selectedAppIds, availableIds) {
        val normalizedSelection = selectedAppIds
            .filter { availableIds.contains(it) }
            .distinct()
        if (normalizedSelection != selectedAppIds) {
            selectedAppIds = normalizedSelection
            return@LaunchedEffect
        }
        onSelectedAppIdsChange(normalizedSelection)
    }

    BackHandler(onBack = onBack)

    AppSubScreen(
        title = "Apps permitidas",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Apps en Inicio",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (orderedEnabledApps.isEmpty()) {
                            Text(
                                text = "Pulsa + en la lista inferior para añadir apps.",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        } else {
                            orderedEnabledApps.forEachIndexed { index, option ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.label,
                                            fontSize = 16.sp,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = if (option.isMiniApp) "Mini app" else "App instalada",
                                            fontSize = 13.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val mutable =
                                                orderedEnabledApps.map { it.id }.toMutableList()
                                            mutable.removeAt(index)
                                            applySelection(mutable)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Quitar app",
                                            tint = HeaderColor
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val mutable =
                                                    orderedEnabledApps.map { it.id }.toMutableList()
                                                Collections.swap(mutable, index, index - 1)
                                                applySelection(mutable)
                                            }
                                        },
                                        enabled = index > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Subir app",
                                            tint = HeaderColor
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index < orderedEnabledApps.lastIndex) {
                                                val mutable =
                                                    orderedEnabledApps.map { it.id }.toMutableList()
                                                Collections.swap(mutable, index, index + 1)
                                                applySelection(mutable)
                                            }
                                        },
                                        enabled = index < orderedEnabledApps.lastIndex
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Bajar app",
                                            tint = HeaderColor
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Añadir apps",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Mini apps",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (selectableMiniApps.isEmpty()) {
                            Text(
                                text = "No hay mini apps para añadir.",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        } else {
                            selectableMiniApps.forEach { option ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.label,
                                            fontSize = 16.sp,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = "Mini app",
                                            fontSize = 13.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            applySelection(orderedEnabledApps.map { it.id } + option.id)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Añadir app",
                                            tint = HeaderColor
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Apps instaladas",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (selectableInstalledApps.isEmpty()) {
                            Text(
                                text = "No hay apps instaladas para añadir.",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        } else {
                            selectableInstalledApps.forEach { option ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.label,
                                            fontSize = 16.sp,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = "App instalada",
                                            fontSize = 13.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            applySelection(orderedEnabledApps.map { it.id } + option.id)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Añadir app",
                                            tint = HeaderColor
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            AppBottomSecondaryButton(
                text = "Atrás",
                icon = Icons.Default.ArrowBack,
                onClick = onBack
            )
        }
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 17.sp,
                color = Color.Black
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = HeaderColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.Gray
            )
        )
    }
}

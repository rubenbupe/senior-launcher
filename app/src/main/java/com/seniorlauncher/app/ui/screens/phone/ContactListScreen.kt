package com.seniorlauncher.app.ui.screens.phone

import android.Manifest
import android.R
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.seniorlauncher.app.data.model.Contact
import com.seniorlauncher.app.ui.components.AppBottomPrimaryButton
import com.seniorlauncher.app.ui.components.AppBottomSecondaryButton
import com.seniorlauncher.app.ui.components.AppSubScreen
import com.seniorlauncher.app.ui.theme.ContactCircleColor
import com.seniorlauncher.app.ui.theme.HeaderColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val HEADER_HEIGHT = 40.dp
private val ROW_HEIGHT = 72.dp

private data class NewContactInput(
    val name: String,
    val phone: String,
    val photoBytes: ByteArray?
)

private sealed class ContactListItem {
    data class Header(val letter: String) : ContactListItem()
    data class ContactRow(val contact: Contact) : ContactListItem()
}

@Composable
fun ContactListScreen(
    onBack: () -> Unit,
    onContactClick: (Contact) -> Unit,
    allContacts: List<Contact>
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var flatList by remember { mutableStateOf<List<ContactListItem>>(emptyList()) }
    var letterIndex by remember { mutableStateOf<LinkedHashMap<String, Int>>(LinkedHashMap()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var phoneHasError by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPhotoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isSavingContact by remember { mutableStateOf(false) }
    var pendingContactData by remember { mutableStateOf<NewContactInput?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedPhotoUri = uri
        scope.launch {
            selectedPhotoBytes = withContext(Dispatchers.IO) {
                readAndCompressPhoto(context, uri)
            }
        }
    }

    fun resetAddDialogState() {
        newName = ""
        newPhone = ""
        phoneHasError = false
        selectedPhotoUri = null
        selectedPhotoBytes = null
        pendingContactData = null
        isSavingContact = false
    }

    fun saveContact(input: NewContactInput) {
        scope.launch {
            isSavingContact = true
            val success = withContext(Dispatchers.IO) {
                insertContactSync(context, input.name, input.phone, input.photoBytes)
            }
            isSavingContact = false
            if (success) {
                Toast.makeText(context, "Contacto añadido", Toast.LENGTH_SHORT).show()
                showAddDialog = false
                resetAddDialogState()
            } else {
                Toast.makeText(context, "No se pudo añadir el contacto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val requestWriteContacts = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val input = pendingContactData
        pendingContactData = null
        if (granted && input != null) {
            saveContact(input)
        } else if (!granted) {
            Toast.makeText(context, "Permiso de contactos denegado", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(allContacts) {
        if (allContacts.isEmpty()) {
            flatList = emptyList()
            letterIndex = LinkedHashMap()
            return@LaunchedEffect
        }
        val flat = withContext(Dispatchers.Default) { buildFlatList(allContacts) }
        val index = withContext(Dispatchers.Default) { buildLetterIndex(flat) }
        flatList = flat
        letterIndex = index
    }

    val letters = remember(letterIndex) { letterIndex.keys.toList() }
    val isLoading = allContacts.isNotEmpty() && flatList.isEmpty()

    AppSubScreen(
        title = "Contactos",
        content = {
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = HeaderColor)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f)
                    ) {
                        items(
                            items = flatList,
                            key = { item ->
                                when (item) {
                                    is ContactListItem.Header -> "h_${item.letter}"
                                    is ContactListItem.ContactRow -> "c_${item.contact.id}"
                                }
                            },
                            contentType = { item ->
                                when (item) {
                                    is ContactListItem.Header -> 0
                                    is ContactListItem.ContactRow -> 1
                                }
                            }
                        ) { item ->
                            when (item) {
                                is ContactListItem.Header -> SectionHeader(item.letter)
                                is ContactListItem.ContactRow -> ContactRow(
                                    contact = item.contact,
                                    context = context,
                                    onClick = { onContactClick(item.contact) }
                                )
                            }
                        }
                    }
                }
                if (!isLoading) {
                    AlphabetStrip(
                        letters = letters,
                        onLetterSelected = { letter ->
                            val idx = letterIndex[letter] ?: return@AlphabetStrip
                            scope.launch { listState.scrollToItem(idx) }
                        },
                        onStripHeightChanged = { }
                    )
                }
            }
        },
        bottomBar = {
            AppBottomPrimaryButton(
                text = "Añadir contacto",
                icon = Icons.Default.PersonAdd,
                onClick = { showAddDialog = true }
            )
            AppBottomSecondaryButton(
                text = "Atrás",
                icon = Icons.Default.ArrowBack,
                onClick = onBack
            )
        }
    )

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSavingContact) {
                    showAddDialog = false
                    resetAddDialogState()
                }
            },
            title = { Text("Nuevo contacto") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = {
                            newPhone = it
                            phoneHasError = it.isNotBlank() && !isValidPhone(it)
                        },
                        label = { Text("Teléfono") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = phoneHasError,
                        supportingText = {
                            if (phoneHasError) {
                                Text("Formato válido: +34600111222 o 600111222")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Foto",
                        color = Color.Black,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(ContactCircleColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedPhotoUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(selectedPhotoUri)
                                        .crossfade(false)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_menu_camera),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(onClick = { photoPicker.launch("image/*") }) {
                            Text("Seleccionar foto")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSavingContact,
                    modifier = Modifier.height(64.dp),
                    onClick = {
                        val name = newName.trim()
                        val phone = newPhone.trim()
                        if (name.isBlank() || phone.isBlank()) {
                            Toast.makeText(
                                context,
                                "Nombre y teléfono son obligatorios",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        if (!isValidPhone(phone)) {
                            phoneHasError = true
                            Toast.makeText(
                                context,
                                "El teléfono no tiene un formato válido",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        val normalizedPhone = normalizePhone(phone)
                        val input = NewContactInput(name, normalizedPhone, selectedPhotoBytes)
                        if (hasWriteContactsPermission(context)) {
                            saveContact(input)
                        } else {
                            pendingContactData = input
                            requestWriteContacts.launch(Manifest.permission.WRITE_CONTACTS)
                        }
                    }
                ) {
                    if (isSavingContact) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Guardar")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !isSavingContact,
                    modifier = Modifier.height(64.dp),
                    onClick = {
                        showAddDialog = false
                        resetAddDialogState()
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(letter: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .background(Color(0xFFEEEEEE)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = HeaderColor
        )
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    context: Context,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactPhoto(contact = contact, context = context, size = 48.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = contact.name,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
    }
}

@Composable
private fun AlphabetStrip(
    letters: List<String>,
    onLetterSelected: (String) -> Unit,
    onStripHeightChanged: (Int) -> Unit
) {
    var stripSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .width(32.dp)
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .onSizeChanged {
                stripSize = it
                onStripHeightChanged(it.height)
            }
            .pointerInput(letters, stripSize) {
                if (letters.isEmpty() || stripSize.height <= 0) return@pointerInput
                detectTapGestures { offset ->
                    val i = (offset.y / stripSize.height * letters.size).toInt()
                        .coerceIn(0, letters.size - 1)
                    onLetterSelected(letters[i])
                }
            }
            .pointerInput(letters, stripSize) {
                if (letters.isEmpty() || stripSize.height <= 0) return@pointerInput
                detectDragGestures { change, _ ->
                    change.consume()
                    val i = (change.position.y / stripSize.height * letters.size).toInt()
                        .coerceIn(0, letters.size - 1)
                    onLetterSelected(letters[i])
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val fontSize = if (letters.isEmpty()) {
                11.sp
            } else {
                with(LocalDensity.current) {
                    val dynamic = ((stripSize.height / letters.size).toDp() * 0.6f).value
                    dynamic.coerceIn(11f, 18f).sp
                }
            }
            letters.forEach { letter ->
                Text(
                    text = letter,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = HeaderColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ContactPhoto(
    contact: Contact,
    context: Context,
    size: Dp,
    onClick: (() -> Unit)? = null
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val imageRequest = remember(contact.photoUri, sizePx) {
        contact.photoUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(sizePx, sizePx)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(false)
                .allowHardware(true)
                .build()
        }
    }
    val initial = contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val fontSize = with(LocalDensity.current) { (size * 0.5f).toSp() }
    val modifier = if (onClick != null) {
        Modifier.size(size).clip(CircleShape).background(ContactCircleColor).clickable(onClick = onClick)
    } else {
        Modifier.size(size).clip(CircleShape).background(ContactCircleColor)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initial,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun buildFlatList(contacts: List<Contact>): List<ContactListItem> {
    val sorted = contacts
        .filter { it.name.isNotBlank() }
        .sortedBy { it.name.uppercase() }
    val result = ArrayList<ContactListItem>(sorted.size + 30)
    var currentLetter = ""
    for (contact in sorted) {
        val first = contact.name.first().uppercaseChar()
        val letter = if (first.isLetter()) first.toString() else "#"
        if (letter != currentLetter) {
            currentLetter = letter
            result.add(ContactListItem.Header(letter))
        }
        result.add(ContactListItem.ContactRow(contact))
    }
    return result
}

private fun buildLetterIndex(flatList: List<ContactListItem>): LinkedHashMap<String, Int> {
    val map = LinkedHashMap<String, Int>()
    for ((index, item) in flatList.withIndex()) {
        if (item is ContactListItem.Header) {
            map[item.letter] = index
        }
    }
    return map
}

private fun hasWriteContactsPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun normalizePhone(raw: String): String {
    return raw
        .replace(" ", "")
        .replace("-", "")
        .replace("(", "")
        .replace(")", "")
}

private fun isValidPhone(raw: String): Boolean {
    val normalized = normalizePhone(raw)
    return Regex("^\\+?[0-9]{6,15}$").matches(normalized)
}

private fun readAndCompressPhoto(context: Context, uri: Uri): ByteArray? {
    val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)
    } ?: return null
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray()
}

private fun insertContactSync(
    context: Context,
    name: String,
    phone: String,
    photoBytes: ByteArray?
): Boolean {
    val operations = ArrayList<ContentProviderOperation>()
    val googleAccount = resolveDefaultGoogleAccount(context)

    operations.add(
        ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, googleAccount?.second)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, googleAccount?.first)
            .build()
    )

    operations.add(
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            )
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            .build()
    )

    operations.add(
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            )
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
            .withValue(
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            )
            .build()
    )

    if (photoBytes != null) {
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                .build()
        )
    }

    return try {
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
        true
    } catch (_: Exception) {
        false
    }
}

private fun resolveDefaultGoogleAccount(context: Context): Pair<String, String>? {
    val settingsProjection = arrayOf(
        ContactsContract.Settings.ACCOUNT_NAME,
        ContactsContract.Settings.ACCOUNT_TYPE,
        ContactsContract.Settings.SHOULD_SYNC
    )
    context.contentResolver.query(
        ContactsContract.Settings.CONTENT_URI,
        settingsProjection,
        "${ContactsContract.Settings.ACCOUNT_TYPE} = ? AND ${ContactsContract.Settings.SHOULD_SYNC} = 1",
        arrayOf("com.google"),
        null
    )?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(ContactsContract.Settings.ACCOUNT_NAME)
        val typeIdx = cursor.getColumnIndex(ContactsContract.Settings.ACCOUNT_TYPE)
        if (cursor.moveToFirst()) {
            val accountName = cursor.getString(nameIdx).orEmpty()
            val accountType = cursor.getString(typeIdx).orEmpty()
            if (accountName.isNotBlank() && accountType == "com.google") {
                return accountName to accountType
            }
        }
    }

    val rawProjection = arrayOf(
        ContactsContract.RawContacts.ACCOUNT_NAME,
        ContactsContract.RawContacts.ACCOUNT_TYPE
    )
    context.contentResolver.query(
        ContactsContract.RawContacts.CONTENT_URI,
        rawProjection,
        "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?",
        arrayOf("com.google"),
        "${ContactsContract.RawContacts._ID} ASC"
    )?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
        val typeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
        while (cursor.moveToNext()) {
            val accountName = cursor.getString(nameIdx).orEmpty()
            val accountType = cursor.getString(typeIdx).orEmpty()
            if (accountName.isNotBlank() && accountType == "com.google") {
                return accountName to accountType
            }
        }
    }

    return null
}

fun loadAllContactsSync(context: Context): List<Contact> {
    val contactMap = mutableMapOf<Long, Pair<String, Uri?>>()
    val projection = arrayOf(
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        ContactsContract.Contacts.PHOTO_URI
    )
    context.contentResolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        projection,
        null,
        null,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
        val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        val photoIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIdx)
            val name = cursor.getString(nameIdx) ?: continue
            if (name.isBlank()) continue
            val photoUri = cursor.getString(photoIdx)?.let { Uri.parse(it) }
            contactMap[id] = name to photoUri
        }
    }
    val phoneMap = loadPhoneMap(context, contactMap.keys)
    return contactMap.map { (id, pair) ->
        Contact(id, pair.first, pair.second, phoneMap[id])
    }
}

private fun loadPhoneMap(context: Context, contactIds: Set<Long>): Map<Long, String> {
    if (contactIds.isEmpty()) return emptyMap()
    val map = mutableMapOf<Long, String>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        null,
        null,
        null
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIdx)
            if (id in contactIds && id !in map) {
                map[id] = cursor.getString(numIdx) ?: ""
            }
        }
    }
    return map
}

package com.seniorlauncher.app.ui.screens.phone

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.seniorlauncher.app.data.model.Contact
import com.seniorlauncher.app.ui.components.AppBottomPrimaryButton
import com.seniorlauncher.app.ui.components.AppBottomSecondaryButton
import com.seniorlauncher.app.ui.components.AppSubScreen
import com.seniorlauncher.app.ui.theme.ContactCircleColor
import com.seniorlauncher.app.ui.theme.HeaderColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

private val HEADER_HEIGHT = 40.dp
private val ROW_HEIGHT = 72.dp
private const val FAVORITES_SYMBOL = "★"
private val DIACRITICS_REGEX = "\\p{Mn}+".toRegex()
private val SIDEBAR_SYMBOLS = buildList {
    add(FAVORITES_SYMBOL)
    addAll(('A'..'Z').map { it.toString() })
    add("#")
}

private sealed class ContactListItem {
    data object FavoritesHeader : ContactListItem()
    data class Header(val letter: String) : ContactListItem()
    data class ContactRow(val contact: Contact) : ContactListItem()
}

@Composable
fun ContactListScreen(
    onBack: () -> Unit,
    onContactClick: (Contact) -> Unit,
    onDialerClick: () -> Unit,
    allContacts: List<Contact>,
    favoriteContactIds: Set<Long>
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var flatList by remember { mutableStateOf<List<ContactListItem>>(emptyList()) }
    var letterIndex by remember { mutableStateOf<LinkedHashMap<String, Int>>(LinkedHashMap()) }

    LaunchedEffect(allContacts, favoriteContactIds) {
        if (allContacts.isEmpty()) {
            flatList = emptyList()
            letterIndex = LinkedHashMap()
            return@LaunchedEffect
        }
        val flat = withContext(Dispatchers.Default) {
            buildFlatList(allContacts, favoriteContactIds)
        }
        val index = withContext(Dispatchers.Default) { buildLetterIndex(flat) }
        flatList = flat
        letterIndex = index
    }

    val letters = remember { SIDEBAR_SYMBOLS }
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
                                    is ContactListItem.FavoritesHeader -> "h_fav"
                                    is ContactListItem.Header -> "h_${item.letter}"
                                    is ContactListItem.ContactRow -> item.contact.id
                                }
                            },
                            contentType = { item ->
                                when (item) {
                                    is ContactListItem.FavoritesHeader -> 0
                                    is ContactListItem.Header -> 1
                                    is ContactListItem.ContactRow -> 2
                                }
                            }
                        ) { item ->
                            when (item) {
                                is ContactListItem.FavoritesHeader -> FavoritesSectionHeader()
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
                            val idx = findNextIndexForSymbol(letter, letters, letterIndex)
                                ?: return@AlphabetStrip
                            scope.launch { listState.scrollToItem(idx) }
                        },
                        onStripHeightChanged = { }
                    )
                }
            }
        },
        bottomBar = {
            AppBottomPrimaryButton(
                text = "Marcar",
                icon = Icons.Default.Dialpad,
                onClick = onDialerClick
            )
            AppBottomSecondaryButton(
                text = "Atrás",
                icon = Icons.Default.ArrowBack,
                onClick = onBack
            )
        }
    )
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
private fun FavoritesSectionHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .background(Color(0xFFEEEEEE))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = HeaderColor
            )
            Text(
                text = "Favoritos",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = HeaderColor
            )
        }
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
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low
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

private fun buildFlatList(
    contacts: List<Contact>,
    favoriteContactIds: Set<Long>
): List<ContactListItem> {
    val sorted = contacts
        .filter { it.name.isNotBlank() }
        .sortedWith(
            compareBy<Contact> { normalizeNameForSorting(it.name) }
                .thenBy { it.name.uppercase(Locale.ROOT) }
        )
    val favorites = sorted.filter { it.id in favoriteContactIds }
    val others = sorted.filter { it.id !in favoriteContactIds }

    val result = ArrayList<ContactListItem>(sorted.size + 32)

    if (favorites.isNotEmpty()) {
        result.add(ContactListItem.FavoritesHeader)
        for (contact in favorites) {
            result.add(ContactListItem.ContactRow(contact))
        }
    }

    var currentLetter = ""
    for (contact in others) {
        val letter = mapContactToGroup(contact.name)
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
            map.putIfAbsent(item.letter, index)
        } else if (item is ContactListItem.FavoritesHeader) {
            map.putIfAbsent(FAVORITES_SYMBOL, index)
        }
    }
    return map
}

private fun mapContactToGroup(name: String): String {
    val firstChar = name.trim().firstOrNull() ?: return "#"
    val normalized = normalizeForGrouping(firstChar)
    return if (normalized in 'A'..'Z') normalized.toString() else "#"
}

private fun normalizeForGrouping(char: Char): Char {
    val normalized = Normalizer
        .normalize(char.toString(), Normalizer.Form.NFD)
        .replace(DIACRITICS_REGEX, "")
        .uppercase(Locale.ROOT)
    return normalized.firstOrNull() ?: char.uppercaseChar()
}

private fun normalizeNameForSorting(name: String): String {
    return Normalizer
        .normalize(name.trim(), Normalizer.Form.NFD)
        .replace(DIACRITICS_REGEX, "")
        .uppercase(Locale.ROOT)
}

private fun findNextIndexForSymbol(
    selectedSymbol: String,
    allSymbols: List<String>,
    indexMap: Map<String, Int>
): Int? {
    val start = allSymbols.indexOf(selectedSymbol).takeIf { it >= 0 } ?: return null
    for (symbolIndex in start until allSymbols.size) {
        val symbol = allSymbols[symbolIndex]
        val index = indexMap[symbol]
        if (index != null) return index
    }

    for (symbolIndex in (start - 1) downTo 0) {
        val symbol = allSymbols[symbolIndex]
        val index = indexMap[symbol]
        if (index != null) return index
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

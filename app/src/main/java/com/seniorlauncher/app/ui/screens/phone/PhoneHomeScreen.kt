package com.seniorlauncher.app.ui.screens.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seniorlauncher.app.data.model.Contact
import com.seniorlauncher.app.ui.components.AppBottomPrimaryButton
import com.seniorlauncher.app.ui.components.AppBottomSecondaryButton
import com.seniorlauncher.app.ui.components.AppSubScreen

@Composable
fun PhoneHomeScreen(
    favoriteContacts: List<Contact>,
    missedCallsByContactId: Map<Long, Int>,
    onBack: () -> Unit,
    onFavoriteContactClick: (Contact) -> Unit,
    onContactClick: (Contact) -> Unit,
    onAllContactsClick: () -> Unit,
    onDialerClick: () -> Unit
) {
    val context = LocalContext.current
    AppSubScreen(
        title = "Teléfono",
        content = {
            if (favoriteContacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No hay contactos favoritos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(favoriteContacts, key = { it.id }) { contact ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFavoriteContactClick(contact) }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                ContactPhoto(contact = contact, context = context, size = 120.dp)
                                val missed = missedCallsByContactId[contact.id] ?: 0
                                if (missed > 0) {
                                    Text(
                                        text = if (missed > 99) "99+" else missed.toString(),
                                        color = MaterialTheme.colorScheme.onError,
                                        fontSize = 14.sp,
                                        modifier = Modifier
                                            .padding(4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.size(10.dp))
                            Text(
                                text = contact.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            AppBottomPrimaryButton(
                text = "Todos los contactos",
                icon = Icons.Default.Contacts,
                onClick = onAllContactsClick
            )
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

package com.seniorlauncher.app.ui.screens.sms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seniorlauncher.app.data.model.SmsConversation
import com.seniorlauncher.app.ui.components.AppBottomPrimaryButton
import com.seniorlauncher.app.ui.components.AppBottomSecondaryButton
import com.seniorlauncher.app.ui.components.AppSubScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SmsListScreen(
    conversations: List<SmsConversation>,
    isLoading: Boolean,
    showDefaultAppDialog: Boolean,
    onDismissDefaultDialog: () -> Unit,
    onRequestDefaultApp: () -> Unit,
    onBack: () -> Unit,
    onThreadClick: (SmsConversation) -> Unit,
    onMarkAllRead: () -> Unit,
    onDeleteConversation: (Long) -> Unit
) {
    val showBlockingLoader = isLoading && conversations.isEmpty()

    AppSubScreen(
        title = "Mensajes",
        content = {
            if (showBlockingLoader) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (conversations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay mensajes", color = Color.Gray, fontSize = 18.sp)
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(conversations, key = { it.threadId }) { conversation ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onThreadClick(conversation) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = conversation.address,
                                        color = Color.Black,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = conversation.preview,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp,
                                        maxLines = 3
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatSmsTime(conversation.timestamp),
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                    if (conversation.unreadCount > 0) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(Color.Red, CircleShape)
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            AppBottomPrimaryButton(
                text = "Marcar todo leído",
                icon = Icons.Default.DoneAll,
                onClick = onMarkAllRead
            )
            AppBottomSecondaryButton(
                text = "Atrás",
                icon = Icons.Default.ArrowBack,
                onClick = onBack
            )
        }
    )

    if (showDefaultAppDialog) {
        AlertDialog(
            onDismissRequest = onDismissDefaultDialog,
            title = { Text("SMS predeterminada") },
            text = { Text("Para gestionar SMS, establece Senior Launcher como app de SMS predeterminada.") },
            confirmButton = {
                TextButton(onClick = onRequestDefaultApp) { Text("Configurar") }
            },
            dismissButton = {
                TextButton(onClick = onDismissDefaultDialog) { Text("Cancelar") }
            }
        )
    }
}

private fun formatSmsTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val oneDayMs = 24 * 60 * 60 * 1000L

    return when {
        diff < oneDayMs -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        diff < 7 * oneDayMs -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))
    }
}

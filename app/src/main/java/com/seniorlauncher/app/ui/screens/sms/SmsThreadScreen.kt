package com.seniorlauncher.app.ui.screens.sms

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seniorlauncher.app.data.model.SmsItem
import com.seniorlauncher.app.ui.components.AppBottomPrimaryButton
import com.seniorlauncher.app.ui.components.AppBottomSecondaryButton
import com.seniorlauncher.app.ui.components.AppSubScreen
import com.seniorlauncher.app.ui.theme.HeaderColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SmsThreadScreen(
    title: String,
    messages: List<SmsItem>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onDeleteConversation: () -> Unit
) {
    val showBlockingLoader = isLoading && messages.isEmpty()

    AppSubScreen(
        title = title.ifBlank { "Chat" },
        content = {
            if (showBlockingLoader) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HeaderColor)
                }
            } else if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sin mensajes en este chat", color = Color.Gray, fontSize = 18.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(messages.asReversed(), key = { it.id }) { sms ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (sms.isSentByMe) Arrangement.End else Arrangement.Start
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .background(
                                        if (sms.isSentByMe) HeaderColor.copy(alpha = 0.15f) else Color(0xFFF0F0F0),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = sms.body.ifBlank { "(vacío)" },
                                    color = Color.Black,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = formatSmsTime(sms.timestamp),
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            AppBottomPrimaryButton(
                text = "Borrar mensajes",
                icon = Icons.Default.Delete,
                onClick = onDeleteConversation
            )
            AppBottomSecondaryButton(
                text = "Atrás",
                icon = Icons.Default.ArrowBack,
                onClick = onBack
            )
        }
    )
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

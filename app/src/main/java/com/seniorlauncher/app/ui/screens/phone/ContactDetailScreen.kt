package com.seniorlauncher.app.ui.screens.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seniorlauncher.app.R
import com.seniorlauncher.app.data.model.Contact
import com.seniorlauncher.app.ui.components.AppBottomPrimaryButton
import com.seniorlauncher.app.ui.components.AppBottomSecondaryButton
import com.seniorlauncher.app.ui.components.AppSubScreen

@Composable
fun ContactDetailScreen(
    contact: Contact,
    useWhatsApp: Boolean,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    val context = LocalContext.current
    AppSubScreen(
        title = "Contacto",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ContactPhoto(contact = contact, context = context, size = 128.dp)
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = contact.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = contact.phone.orEmpty().ifBlank { "Sin número" },
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        bottomBar = {
            if (useWhatsApp) {
                AppBottomPrimaryButton(
                    text = "WhatsApp",
                    icon = ImageVector.vectorResource(R.drawable.ic_whatsapp),
                    onClick = onWhatsApp
                )
            }
            AppBottomPrimaryButton(
                text = "Llamar",
                icon = Icons.Default.Call,
                onClick = onCall
            )
            AppBottomSecondaryButton(
                text = "Atrás",
                icon = Icons.Default.ArrowBack,
                onClick = onBack
            )
        }
    )
}

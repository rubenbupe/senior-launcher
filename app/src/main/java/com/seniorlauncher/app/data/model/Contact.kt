package com.seniorlauncher.app.data.model

import android.net.Uri

data class Contact(
    val id: Long,
    val name: String,
    val photoUri: Uri?,
    val phone: String?
)
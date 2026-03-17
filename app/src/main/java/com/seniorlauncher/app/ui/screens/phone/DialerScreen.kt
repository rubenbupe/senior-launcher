package com.seniorlauncher.app.ui.screens.phone

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seniorlauncher.app.ui.components.AppBottomPrimaryButton
import com.seniorlauncher.app.ui.components.AppBottomSecondaryButton
import com.seniorlauncher.app.ui.components.AppSubScreen

@Composable
fun DialerScreen(
    initialNumber: String = "",
    onBack: () -> Unit,
    onCallRequested: (String) -> Unit,
    onCallStarted: () -> Unit = {}
) {
    var number by remember(initialNumber) { mutableStateOf(initialNumber) }
    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 80) }.getOrNull()
    }

    DisposableEffect(Unit) {
        onDispose { toneGenerator?.release() }
    }

    AppSubScreen(
        title = "Marcar",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = number.ifEmpty { " " },
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
														Box(
																modifier = Modifier
																		.background(
																				MaterialTheme.colorScheme.surfaceVariant
																		)
																		.clickable {
																				if (number.isNotEmpty()) number = number.dropLast(1)
																		},
																contentAlignment = Alignment.Center
														) {
																Text(
																		text = "BORRAR",
																		fontSize = 12.sp,
																		fontWeight = FontWeight.Medium,
																		color = MaterialTheme.colorScheme.onSurface
																)
														}
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val keySize = ((maxWidth - 36.dp) / 3f).coerceAtLeast(72.dp)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("*", "0", "#")
                        ).forEach { rowKeys ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    18.dp,
                                    Alignment.CenterHorizontally
                                )
                            ) {
                                rowKeys.forEach { key ->
                                    Box(
                                        modifier = Modifier
                                            .size(keySize)
                                            .clip(CircleShape)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                CircleShape
                                            )
                                            .clickable {
                                                number += key
                                                playDtmfTone(toneGenerator, key)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .clickable {
                                if (number.isNotEmpty()) number = number.dropLast(1)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backspace,
                            contentDescription = "Borrar",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            AppBottomPrimaryButton(
                text = "Llamar",
                icon = Icons.Default.Call,
                onClick = {
                    val num = number.trim()
                    if (num.isNotBlank()) {
                        onCallRequested(num)
                    }
                }
            )
            AppBottomSecondaryButton(
                text = "Atrás",
                icon = Icons.Default.ArrowBack,
                onClick = onBack
            )
        }
    )
}

private fun playDtmfTone(toneGenerator: ToneGenerator?, key: String) {
    val tone = when (key) {
        "0" -> ToneGenerator.TONE_DTMF_0
        "1" -> ToneGenerator.TONE_DTMF_1
        "2" -> ToneGenerator.TONE_DTMF_2
        "3" -> ToneGenerator.TONE_DTMF_3
        "4" -> ToneGenerator.TONE_DTMF_4
        "5" -> ToneGenerator.TONE_DTMF_5
        "6" -> ToneGenerator.TONE_DTMF_6
        "7" -> ToneGenerator.TONE_DTMF_7
        "8" -> ToneGenerator.TONE_DTMF_8
        "9" -> ToneGenerator.TONE_DTMF_9
        "*" -> ToneGenerator.TONE_DTMF_S
        "#" -> ToneGenerator.TONE_DTMF_P
        else -> null
    } ?: return

    runCatching {
        toneGenerator?.startTone(tone, 120)
    }
}

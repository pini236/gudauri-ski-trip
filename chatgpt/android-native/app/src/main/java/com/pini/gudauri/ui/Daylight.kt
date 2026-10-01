package com.pini.gudauri.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pini.gudauri.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant

val LocalDaylight = staticCompositionLocalOf { GudauriTime.at(Instant.now(), ThemeMode.AUTO) }

@Composable fun rememberForegroundTime(): Instant {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(Instant.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = Instant.now()
                delay(60_000 - value.toEpochMilli() % 60_000)
            }
        }
    }
    return now
}

@Composable fun ThemeControl(onTheme: () -> Unit) {
    val mode = LocalDaylight.current.mode
    TextButton(onClick = onTheme, modifier = Modifier.semantics {
        contentDescription = "מצב תצוגה: ${mode.label}. מעבר ל${mode.next().label}"
    }) {
        Text(mode.icon, fontSize = 21.sp)
        Text(" ${mode.label}", fontSize = 12.sp)
    }
}

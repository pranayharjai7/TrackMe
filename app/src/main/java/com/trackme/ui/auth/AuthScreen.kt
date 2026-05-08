package com.trackme.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AuthScreen(onAuthSuccess: (isNewUser: Boolean) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = { onAuthSuccess(false) }) { Text("Sign In (placeholder)") }
    }
}

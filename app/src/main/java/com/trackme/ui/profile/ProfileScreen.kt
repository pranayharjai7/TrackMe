package com.trackme.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Profile — coming in Plan 3", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSignOut) { Text("Sign Out") }
    }
}

package com.trackme.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.trackme.BuildConfig
import com.trackme.R
import com.trackme.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthSuccess: (isNewUser: Boolean) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Entry animation — triggers once on first composition
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entryAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "entryAlpha",
    )
    val entryOffset by animateFloatAsState(
        targetValue = if (entered) 0f else 48f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "entryOffset",
    )

    // Infinite ambient animations
    val infinite = rememberInfiniteTransition(label = "ambient")

    // Floating orb positions
    val orb1 by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
        label = "orb1",
    )
    val orb2 by infinite.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "orb2",
    )
    val orb3 by infinite.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5500, easing = LinearEasing), RepeatMode.Reverse),
        label = "orb3",
    )
    val orb4 by infinite.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Reverse),
        label = "orb4",
    )

    // Icon pulse
    val iconPulse by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "iconPulse",
    )
    // Subtle glow ring pulse
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        // Animated floating orbs background
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                color = Violet.copy(alpha = 0.13f),
                radius = 200.dp.toPx(),
                center = Offset(w * 0.12f, h * (0.05f + orb1 * 0.12f)),
            )
            drawCircle(
                color = Blue.copy(alpha = 0.09f),
                radius = 160.dp.toPx(),
                center = Offset(w * 0.88f, h * (0.15f + orb2 * 0.18f)),
            )
            drawCircle(
                color = Coral.copy(alpha = 0.07f),
                radius = 120.dp.toPx(),
                center = Offset(w * 0.75f, h * (0.72f - orb3 * 0.08f)),
            )
            drawCircle(
                color = Teal.copy(alpha = 0.07f),
                radius = 90.dp.toPx(),
                center = Offset(w * 0.18f, h * (0.80f + orb4 * 0.06f)),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset(y = entryOffset.dp)
                .alpha(entryAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Pulsing icon with glow ring
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Violet.copy(alpha = glowAlpha), Color.Transparent),
                            ),
                            CircleShape,
                        )
                )
                // Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(iconPulse)
                        .background(
                            Brush.linearGradient(listOf(Violet.copy(alpha = 0.25f), Blue.copy(alpha = 0.15f))),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = Violet,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Gradient title
            Text(
                text = "TrackMe",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    brush = Brush.horizontalGradient(listOf(Violet, Blue)),
                ),
            )
            Text(
                text = "Your personal gym companion",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMuted,
                modifier = Modifier.padding(bottom = 28.dp),
            )

            // Auth card
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp)) {

                    PillTabRow(
                        selectedIndex = if (isSignUp) 1 else 0,
                        onTabSelected = { isSignUp = it == 1 },
                    )

                    Spacer(Modifier.height(24.dp))

                    // Form slides left/right on tab switch
                    AnimatedContent(
                        targetState = isSignUp,
                        transitionSpec = {
                            if (targetState) {
                                (slideInHorizontally { it / 2 } + fadeIn(tween(220))) togetherWith
                                        (slideOutHorizontally { -it / 2 } + fadeOut(tween(180)))
                            } else {
                                (slideInHorizontally { -it / 2 } + fadeIn(tween(220))) togetherWith
                                        (slideOutHorizontally { it / 2 } + fadeOut(tween(180)))
                            }
                        },
                        label = "formContent",
                    ) { signUp ->
                        Column {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it; viewModel.clearError() },
                                label = { Text("Email") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Violet,
                                    focusedLabelColor = Violet,
                                    focusedLeadingIconColor = Violet,
                                ),
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; viewModel.clearError() },
                                label = { Text(if (signUp) "Create password" else "Password") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                            tint = OnSurfaceMuted,
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Violet,
                                    focusedLabelColor = Violet,
                                    focusedLeadingIconColor = Violet,
                                ),
                            )
                        }
                    }

                    AnimatedVisibility(visible = uiState.error != null) {
                        uiState.error?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Primary action button
                    Button(
                        onClick = {
                            if (isSignUp) viewModel.signUpWithEmail(email, password, onAuthSuccess)
                            else viewModel.signInWithEmail(email, password, onAuthSuccess)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Violet,
                            contentColor = Color.White,
                            disabledContainerColor = Violet.copy(alpha = 0.5f),
                        ),
                    ) {
                        AnimatedContent(
                            targetState = uiState.isLoading,
                            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                            label = "btnState",
                        ) { loading ->
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                )
                            } else {
                                Text(
                                    if (isSignUp) "Create Account" else "Sign In",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = OnSurfaceMuted.copy(alpha = 0.2f),
                        )
                        Text(
                            "  or  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMuted,
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = OnSurfaceMuted.copy(alpha = 0.2f),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Google sign-in button
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val credentialManager = CredentialManager.create(context)
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                                    .setAutoSelectEnabled(false)
                                    .build()
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()
                                try {
                                    val result = credentialManager.getCredential(context, request)
                                    val cred = result.credential
                                    if (cred is androidx.credentials.CustomCredential &&
                                        cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                    ) {
                                        val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                                        viewModel.signInWithGoogle(idToken, onAuthSuccess)
                                    } else {
                                        viewModel.setError("Unexpected credential type")
                                    }
                                } catch (_: GetCredentialCancellationException) {
                                    // user dismissed
                                } catch (e: GetCredentialException) {
                                    viewModel.setError(e.message ?: "Google sign in failed")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !uiState.isLoading,
                        border = BorderStroke(1.dp, OnSurfaceMuted.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = OnSurface,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_google),
                            contentDescription = "Google",
                            modifier = Modifier.size(20.dp),
                            tint = Color.Unspecified,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Continue with Google",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PillTabRow(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    val pillOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pillOffset",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(SurfaceVariant, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        val pillWidth = maxWidth / 2

        // Animated sliding pill
        Box(
            modifier = Modifier
                .width(pillWidth)
                .fillMaxHeight()
                .offset(x = pillWidth * pillOffset)
                .background(
                    Brush.horizontalGradient(listOf(Violet, Blue.copy(alpha = 0.8f))),
                    RoundedCornerShape(10.dp),
                ),
        )

        Row(Modifier.fillMaxSize()) {
            listOf("Sign In", "Sign Up").forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedIndex == index) Color.White else OnSurfaceMuted,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

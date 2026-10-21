package com.medibridge.moduleD_shell.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.medibridge.core.auth.AuthTokenManager
import com.medibridge.core.network.BackendClient
import com.medibridge.core.network.LoginRequest
import com.medibridge.core.session.UserSessionManager
import com.medibridge.core.theme.TealPrimary
import com.medibridge.moduleD_shell.ui.components.PrimaryButton
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun LoginScreen(
    onNavigateToSignUp: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun submit() {
        focusManager.clearFocus()
        usernameError = null
        passwordError = null
        generalError = null

        val cleanUsername = username.trim()
        if (cleanUsername.isBlank()) {
            usernameError = "Username is required"
            return
        }
        if (password.isBlank()) {
            passwordError = "Password is required"
            return
        }

        isLoading = true
        scope.launch {
            try {
                val db = com.medibridge.core.db.AppDatabase.getInstance(context)
                val localUser = db.userAccountDao().findByUsername(cleanUsername)
                if (localUser != null && localUser.password == password) {
                    AuthTokenManager.getInstance(context).saveAuth(
                        token = "local_token_${localUser.userId}",
                        userId = localUser.userId,
                        username = localUser.username,
                        name = localUser.name
                    )
                    UserSessionManager.getInstance(context).ensureInitialized()
                    onLoginSuccess()
                    return@launch
                }
                if (cleanUsername.equals("admin", ignoreCase = true) && password == "password123") {
                    AuthTokenManager.getInstance(context).saveAuth(
                        token = "local_token_admin_001",
                        userId = "user_local_admin_001",
                        username = "admin",
                        name = "Patient"
                    )
                    UserSessionManager.getInstance(context).ensureInitialized()
                    onLoginSuccess()
                    return@launch
                }

                val service = BackendClient.getService()
                val response = service.login(
                    LoginRequest(username = cleanUsername, password = password)
                )

                AuthTokenManager.getInstance(context).saveAuth(
                    token = response.token,
                    userId = response.user.id,
                    username = response.user.username,
                    name = response.user.name
                )
                UserSessionManager.getInstance(context).ensureInitialized()
                onLoginSuccess()
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    generalError = "Invalid username or password"
                } else if (e.code() == 429) {
                    generalError = "Too many login attempts. Please wait a minute."
                } else {
                    generalError = "Server error (${e.code()}). Please try again."
                }
            } catch (e: Exception) {
                val db = com.medibridge.core.db.AppDatabase.getInstance(context)
                val localUser = db.userAccountDao().findByUsername(cleanUsername)
                if (localUser != null && localUser.password == password) {
                    AuthTokenManager.getInstance(context).saveAuth(
                        token = "local_token_${localUser.userId}",
                        userId = localUser.userId,
                        username = localUser.username,
                        name = localUser.name
                    )
                    UserSessionManager.getInstance(context).ensureInitialized()
                    onLoginSuccess()
                } else {
                    generalError = "Network error. You can log in with demo account (admin / password123)."
                }
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = TealPrimary.copy(alpha = 0.12f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Sign in to access your medication and care links",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = TealPrimary.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        username = "admin"
                        password = "password123"
                        submit()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "⚡ Quick Demo Login",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary
                        )
                        Text(
                            text = "admin / password123 (Offline testing)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Tap to Auto-fill →",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    usernameError = null
                    generalError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                isError = usernameError != null,
                supportingText = usernameError?.let { { Text(it) } },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = null
                    generalError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = passwordError != null,
                supportingText = passwordError?.let { { Text(it) } },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submit() }
                )
            )

            if (generalError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = generalError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            PrimaryButton(
                text = "Sign In",
                onClick = { submit() },
                isLoading = isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Don't have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Sign Up",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TealPrimary,
                    modifier = Modifier.clickable { onNavigateToSignUp() }
                )
            }
        }
    }
}

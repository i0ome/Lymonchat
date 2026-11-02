package com.lymon.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun AuthScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRegister by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            loading = true; error = null
            try { block() } catch (e: Exception) { error = e.localizedMessage ?: "حدث خطأ" }
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.lymon_logo), null, Modifier.width(180.dp))
        Spacer(Modifier.height(16.dp))
        Text(if (isRegister) "إنشاء حساب جديد" else "تسجيل الدخول", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        if (isRegister) {
            OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(email, { email = it }, label = { Text("البريد الإلكتروني") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            pass, { pass = it }, label = { Text("الرمز السري") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))

        Button(
            enabled = !loading && email.isNotBlank() && pass.length >= 6 && (!isRegister || name.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                run { if (isRegister) Repo.register(name.trim(), email.trim(), pass) else Repo.login(email.trim(), pass) }
            }
        ) { Text(if (isRegister) "إنشاء الحساب" else "دخول") }

        OutlinedButton(
            enabled = !loading, modifier = Modifier.fillMaxWidth(),
            onClick = {
                run {
                    val option = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(ctx.getString(R.string.default_web_client_id))
                        .build()
                    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                    val result = CredentialManager.create(ctx).getCredential(ctx, request)
                    val token = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                    Repo.googleLogin(token)
                }
            }
        ) { Text("المتابعة بحساب Google") }

        TextButton(onClick = { isRegister = !isRegister; error = null }) {
            Text(if (isRegister) "عندي حساب؟ سجّل دخول" else "ما عندك حساب؟ أنشئ حساب")
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

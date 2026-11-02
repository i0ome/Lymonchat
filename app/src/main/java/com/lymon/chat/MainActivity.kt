@file:OptIn(ExperimentalMaterial3Api::class)

package com.lymon.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

sealed class Screen {
    object Home : Screen()
    object NewChat : Screen()
    object Admin : Screen()
    data class Room(val chat: Chat) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LymonTheme { App() } }
    }
}

@Composable
fun App() {
    var showSplash by remember { mutableStateOf(true) }
    val uid by Repo.authFlow().collectAsState(initial = Repo.auth.currentUser?.uid)

    when {
        showSplash -> SplashScreen { showSplash = false }
        uid == null -> AuthScreen()
        else -> SignedIn(uid!!)
    }
}

@Composable
fun SignedIn(uid: String) {
    val profile by remember(uid) { Repo.profileFlow(uid) }.collectAsState(initial = null)
    val users by remember { Repo.usersFlow() }.collectAsState(initial = emptyList())
    val me = profile

    if (me == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (me.banned) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("تم حظر حسابك", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("تواصل مع إدارة Lymon chat إذا تعتقد أن هذا خطأ.")
            Spacer(Modifier.height(24.dp))
            Button(onClick = { Repo.logout() }) { Text("تسجيل الخروج") }
        }
        return
    }

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            me = me, users = users,
            onOpen = { screen = Screen.Room(it) },
            onNew = { screen = Screen.NewChat },
            onAdmin = { screen = Screen.Admin }
        )
        Screen.NewChat -> NewChatScreen(
            me = me, users = users,
            onBack = { screen = Screen.Home },
            onCreated = { screen = Screen.Room(it) }
        )
        Screen.Admin -> AdminScreen(me = me, users = users, onBack = { screen = Screen.Home })
        is Screen.Room -> ChatScreen(
            chat = s.chat, me = me, users = users,
            onBack = { screen = Screen.Home }
        )
    }
}

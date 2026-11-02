@file:OptIn(ExperimentalMaterial3Api::class)

package com.lymon.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun Avatar(text: String, size: Int = 44) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(LymonLemon),
        contentAlignment = Alignment.Center
    ) { Text(text.trim().take(1).uppercase(), color = LymonGreen, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun HomeScreen(
    me: UserProfile, users: List<UserProfile>,
    onOpen: (Chat) -> Unit, onNew: () -> Unit, onAdmin: () -> Unit
) {
    val chats by remember(me.uid) { Repo.chatsFlow(me.uid) }.collectAsState(initial = emptyList())
    val byId = users.associateBy { it.uid }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.lymon_icon), null, Modifier.size(32.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Lymon chat", color = LymonGreen)
                    }
                },
                actions = {
                    if (me.role == "admin") {
                        IconButton(onClick = onAdmin) { Icon(Icons.Filled.AdminPanelSettings, "لوحة الإدارة", tint = GoldBadge) }
                    }
                    IconButton(onClick = { Repo.logout() }) { Icon(Icons.AutoMirrored.Filled.Logout, "خروج") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew, containerColor = LymonGreen, contentColor = androidx.compose.ui.graphics.Color.White) {
                Icon(Icons.Filled.Add, "محادثة جديدة")
            }
        }
    ) { pad ->
        if (chats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Text("ما عندك محادثات بعد. اضغط + للبدء")
            }
        } else LazyColumn(Modifier.padding(pad)) {
            items(chats, key = { it.id }) { chat ->
                val title = chat.title(me.uid)
                val otherUid = chat.members.firstOrNull { it != me.uid }
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(chat) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(title)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!chat.group) { Spacer(Modifier.width(4.dp)); BadgeIcon(byId[otherUid]) }
                        }
                        Text(chat.lastMessage, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** محادثة جديدة: اختار شخص واحد = محادثة خاصة، أكثر من شخص + اسم = قروب */
@Composable
fun NewChatScreen(me: UserProfile, users: List<UserProfile>, onBack: () -> Unit, onCreated: (Chat) -> Unit) {
    val scope = rememberCoroutineScope()
    var groupName by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    val list = users.filter { it.uid != me.uid && !it.banned && it.name.contains(query, ignoreCase = true) }
    val isGroup = selected.size > 1 || groupName.isNotBlank()
    val canStart = selected.isNotEmpty() && (!isGroup || groupName.isNotBlank()) && !busy

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("محادثة جديدة") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        },
        bottomBar = {
            Button(
                enabled = canStart,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                onClick = {
                    scope.launch {
                        busy = true
                        val chosen = users.filter { it.uid in selected }
                        val chat = if (isGroup) Repo.createGroup(groupName.trim(), me, chosen) else Repo.openDirect(me, chosen.first())
                        busy = false
                        onCreated(chat)
                    }
                }
            ) { Text(if (isGroup) "إنشاء القروب" else "ابدأ المحادثة") }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            OutlinedTextField(groupName, { groupName = it }, label = { Text("اسم القروب (اتركه فاضي لمحادثة خاصة)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(query, { query = it }, label = { Text("ابحث بالاسم") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn {
                items(list, key = { it.uid }) { u ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = if (u.uid in selected) selected - u.uid else selected + u.uid
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = u.uid in selected, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Avatar(u.name, 36)
                        Spacer(Modifier.width(8.dp))
                        Text(u.name); Spacer(Modifier.width(4.dp)); BadgeIcon(u)
                    }
                }
            }
        }
    }
}

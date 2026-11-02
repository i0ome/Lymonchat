@file:OptIn(ExperimentalMaterial3Api::class)

package com.lymon.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AdminScreen(me: UserProfile, users: List<UserProfile>, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val list = users.filter { it.name.contains(query, true) || it.email.contains(query, true) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("لوحة الإدارة") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("ابحث بالاسم أو البريد") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("عدد المستخدمين: ${users.size}", modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn {
                items(list, key = { it.uid }) { u ->
                    var menu by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u.name, 40)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(u.name, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.width(4.dp)); BadgeIcon(u)
                            }
                            Text(
                                u.email + (if (u.role == "admin") " • مدير" else "") + (if (u.banned) " • محظور" else ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (u.uid != me.uid) {
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "خيارات") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    if (u.badge == "blue") {
                                        DropdownMenuItem(text = { Text("إزالة العلامة الزرقاء") },
                                            onClick = { menu = false; scope.launch { Repo.setBadge(u.uid, "none") } })
                                    } else if (u.role != "admin") {
                                        DropdownMenuItem(text = { Text("توثيق بعلامة زرقاء") },
                                            onClick = { menu = false; scope.launch { Repo.setBadge(u.uid, "blue") } })
                                    }
                                    if (u.role == "admin") {
                                        DropdownMenuItem(text = { Text("إزالة الإدارة") },
                                            onClick = { menu = false; scope.launch { Repo.setAdmin(u.uid, false) } })
                                    } else {
                                        DropdownMenuItem(text = { Text("ترقية لمدير (علامة ذهبية)") },
                                            onClick = { menu = false; scope.launch { Repo.setAdmin(u.uid, true) } })
                                    }
                                    DropdownMenuItem(
                                        text = { Text(if (u.banned) "فك الحظر" else "حظر المستخدم") },
                                        onClick = { menu = false; scope.launch { Repo.setBanned(u.uid, !u.banned) } }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

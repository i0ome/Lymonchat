@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.lymon.chat

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import kotlinx.coroutines.launch
import java.io.File

private val STICKERS = listOf(
    "😀", "😂", "🥰", "😎", "🤩", "😭", "😡", "😴", "🤔", "🙈",
    "👍", "👏", "🙏", "💪", "🤝", "🔥", "❤️", "🎉", "💯", "🍋"
)

@Composable
fun ChatScreen(chat: Chat, me: UserProfile, users: List<UserProfile>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages by remember(chat.id) { Repo.messagesFlow(chat.id) }.collectAsState(initial = emptyList())
    val byId = users.associateBy { it.uid }
    val listState = rememberLazyListState()

    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    var pendingType by remember { mutableStateOf("image") }
    var error by remember { mutableStateOf<String?>(null) }

    // محمّل الصور يدعم GIF المتحركة
    val loader = remember {
        ImageLoader.Builder(ctx).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }.build()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun safe(block: suspend () -> Unit) {
        scope.launch {
            sending = true; error = null
            try { block() } catch (e: Exception) { error = e.localizedMessage ?: "تعذر الإرسال" }
            sending = false
        }
    }

    // اختيار ملف (صورة / GIF / فيديو)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) safe {
            val url = Repo.upload(chat.id, uri)
            Repo.send(chat.id, me, pendingType, url = url)
        }
    }
    fun pick(type: String, mime: String) { pendingType = type; attachMenu = false; picker.launch(mime) }

    // تسجيل الصوت
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recFile by remember { mutableStateOf<File?>(null) }

    fun startRec() {
        try {
            val f = File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            recorder = r; recFile = f
        } catch (e: Exception) { error = "تعذر بدء التسجيل" }
    }

    fun stopRec() {
        val r = recorder; val f = recFile
        recorder = null; recFile = null
        if (r == null || f == null) return
        val ok = try { r.stop(); true } catch (e: Exception) { false }
        r.release()
        if (ok) safe {
            val url = Repo.upload(chat.id, Uri.fromFile(f))
            Repo.send(chat.id, me, "voice", url = url)
            f.delete()
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRec() else error = "نحتاج إذن المايكروفون"
    }

    DisposableEffect(Unit) { onDispose { try { recorder?.release() } catch (_: Exception) {} } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(chat.title(me.uid), 36)
                        Spacer(Modifier.width(10.dp))
                        Text(chat.title(me.uid))
                        if (!chat.group) {
                            Spacer(Modifier.width(4.dp))
                            BadgeIcon(byId[chat.members.firstOrNull { it != me.uid }])
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

                if (showStickers) {
                    FlowRow(Modifier.fillMaxWidth().background(Color(0xFFF6F6F6)).padding(8.dp)) {
                        STICKERS.forEach { s ->
                            Text(s, fontSize = 32.sp, modifier = Modifier.clickable {
                                showStickers = false
                                safe { Repo.send(chat.id, me, "sticker", text = s) }
                            }.padding(8.dp))
                        }
                    }
                }

                if (recorder != null) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FiberManualRecord, null, tint = Color.Red)
                        Spacer(Modifier.width(8.dp))
                        Text("جاري التسجيل... اضغط ■ للإرسال", Modifier.weight(1f))
                        IconButton(onClick = { stopRec() }) { Icon(Icons.Filled.Stop, "إرسال", tint = LymonGreen) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { attachMenu = true }) { Icon(Icons.Filled.AttachFile, "إرفاق") }
                            DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                                DropdownMenuItem(text = { Text("صورة") }, leadingIcon = { Icon(Icons.Filled.Image, null) },
                                    onClick = { pick("image", "image/*") })
                                DropdownMenuItem(text = { Text("GIF") }, leadingIcon = { Icon(Icons.Filled.Gif, null) },
                                    onClick = { pick("gif", "image/gif") })
                                DropdownMenuItem(text = { Text("فيديو") }, leadingIcon = { Icon(Icons.Filled.Videocam, null) },
                                    onClick = { pick("video", "video/*") })
                            }
                        }
                        IconButton(onClick = { showStickers = !showStickers }) { Icon(Icons.Filled.EmojiEmotions, "ملصقات") }
                        OutlinedTextField(
                            text, { text = it }, modifier = Modifier.weight(1f),
                            placeholder = { Text("اكتب رسالة") }, maxLines = 4,
                            shape = RoundedCornerShape(24.dp)
                        )
                        if (text.isBlank()) {
                            IconButton(onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }) {
                                Icon(Icons.Filled.Mic, "تسجيل صوتي", tint = LymonGreen)
                            }
                        } else {
                            IconButton(onClick = {
                                val t = text.trim(); text = ""
                                safe { Repo.send(chat.id, me, "text", text = t) }
                            }) { Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = LymonGreen) }
                        }
                    }
                }
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 8.dp), state = listState) {
            items(messages, key = { it.id }) { m ->
                val mine = m.senderId == me.uid
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    if (m.type == "sticker") {
                        Column {
                            if (chat.group && !mine) SenderName(m, byId[m.senderId])
                            Text(m.text, fontSize = 56.sp)
                        }
                    } else {
                        Column(
                            Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (mine) Color(0xFFDDEFD9) else Color(0xFFF1F1F1)).padding(10.dp)
                        ) {
                            if (chat.group && !mine) SenderName(m, byId[m.senderId])
                            when (m.type) {
                                "image", "gif" -> AsyncImage(
                                    model = m.url, imageLoader = loader, contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                )
                                "video" -> Row(
                                    Modifier.clickable {
                                        try {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(m.url), "video/*"))
                                        } catch (_: Exception) {}
                                    }.padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.PlayCircle, null, tint = LymonGreen, modifier = Modifier.size(40.dp))
                                    Spacer(Modifier.width(8.dp)); Text("تشغيل الفيديو")
                                }
                                "voice" -> VoiceBubble(m.url)
                                else -> Text(m.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SenderName(m: Message, profile: UserProfile?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(m.senderName, style = MaterialTheme.typography.labelMedium, color = LymonGreen)
        Spacer(Modifier.width(3.dp)); BadgeIcon(profile, 14.dp)
    }
}

@Composable
private fun VoiceBubble(url: String) {
    var playing by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer() }
    var prepared by remember { mutableStateOf(false) }
    DisposableEffect(url) { onDispose { try { player.release() } catch (_: Exception) {} } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            try {
                if (playing) { player.pause(); playing = false }
                else if (prepared) { player.start(); playing = true }
                else {
                    player.setDataSource(url)
                    player.setOnPreparedListener { prepared = true; it.start(); playing = true }
                    player.setOnCompletionListener { playing = false; it.seekTo(0) }
                    player.prepareAsync()
                }
            } catch (_: Exception) { playing = false }
        }) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "تشغيل", tint = LymonGreen)
        }
        Text("🎤 رسالة صوتية")
    }
}

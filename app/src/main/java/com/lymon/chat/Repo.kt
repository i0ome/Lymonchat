package com.lymon.chat

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val photo: String = "",
    val role: String = "user",      // user | admin
    val badge: String = "none",     // none | blue | gold
    val banned: Boolean = false
)

data class Chat(
    val id: String = "",
    val name: String = "",
    val group: Boolean = false,
    val members: List<String> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val updatedAt: Long = 0
) {
    fun title(myUid: String): String =
        if (group) name else names.filterKeys { it != myUid }.values.firstOrNull() ?: "محادثة"
}

data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: String = "text",      // text | image | gif | sticker | video | voice
    val text: String = "",
    val url: String = "",
    val createdAt: Long = 0
)

object Repo {
    val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    fun authFlow(): Flow<String?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }

    private suspend fun createProfileIfMissing(uid: String, name: String, email: String, photo: String) {
        val ref = db.collection("users").document(uid)
        if (!ref.get().await().exists()) {
            ref.set(
                mapOf(
                    "name" to name, "email" to email, "photo" to photo,
                    "role" to "user", "badge" to "none", "banned" to false
                )
            ).await()
        }
    }

    suspend fun register(name: String, email: String, pass: String) {
        val u = auth.createUserWithEmailAndPassword(email, pass).await().user!!
        createProfileIfMissing(u.uid, name, email, "")
    }

    suspend fun login(email: String, pass: String) {
        auth.signInWithEmailAndPassword(email, pass).await()
    }

    suspend fun googleLogin(idToken: String) {
        val u = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user!!
        createProfileIfMissing(u.uid, u.displayName ?: "مستخدم", u.email ?: "", u.photoUrl?.toString() ?: "")
    }

    fun logout() = auth.signOut()

    fun profileFlow(uid: String): Flow<UserProfile?> = callbackFlow {
        val l = db.collection("users").document(uid).addSnapshotListener { s, _ ->
            trySend(if (s != null && s.exists()) s.toObject(UserProfile::class.java)?.copy(uid = s.id) else null)
        }
        awaitClose { l.remove() }
    }

    fun usersFlow(): Flow<List<UserProfile>> = callbackFlow {
        val l = db.collection("users").addSnapshotListener { s, _ ->
            trySend(s?.documents?.mapNotNull { d -> d.toObject(UserProfile::class.java)?.copy(uid = d.id) } ?: emptyList())
        }
        awaitClose { l.remove() }
    }

    fun chatsFlow(uid: String): Flow<List<Chat>> = callbackFlow {
        val l = db.collection("chats").whereArrayContains("members", uid).addSnapshotListener { s, _ ->
            val list = s?.documents?.mapNotNull { d -> d.toObject(Chat::class.java)?.copy(id = d.id) } ?: emptyList()
            trySend(list.sortedByDescending { it.updatedAt })
        }
        awaitClose { l.remove() }
    }

    fun messagesFlow(chatId: String): Flow<List<Message>> = callbackFlow {
        val l = db.collection("chats").document(chatId).collection("messages")
            .orderBy("createdAt").addSnapshotListener { s, _ ->
                trySend(s?.documents?.mapNotNull { d -> d.toObject(Message::class.java)?.copy(id = d.id) } ?: emptyList())
            }
        awaitClose { l.remove() }
    }

    suspend fun openDirect(me: UserProfile, other: UserProfile): Chat {
        val id = listOf(me.uid, other.uid).sorted().joinToString("_")
        val members = listOf(me.uid, other.uid)
        val names = mapOf(me.uid to me.name, other.uid to other.name)
        db.collection("chats").document(id)
            .set(mapOf("group" to false, "members" to members, "names" to names), SetOptions.merge()).await()
        return Chat(id = id, group = false, members = members, names = names)
    }

    suspend fun createGroup(name: String, me: UserProfile, others: List<UserProfile>): Chat {
        val all = listOf(me) + others
        val members = all.map { it.uid }
        val names = all.associate { it.uid to it.name }
        val now = System.currentTimeMillis()
        val ref = db.collection("chats").document()
        ref.set(
            mapOf(
                "name" to name, "group" to true, "members" to members, "names" to names,
                "lastMessage" to "", "updatedAt" to now, "createdBy" to me.uid
            )
        ).await()
        return Chat(id = ref.id, name = name, group = true, members = members, names = names)
    }

    suspend fun send(chatId: String, me: UserProfile, type: String, text: String = "", url: String = "") {
        val now = System.currentTimeMillis()
        val chatRef = db.collection("chats").document(chatId)
        chatRef.collection("messages").add(
            mapOf(
                "senderId" to me.uid, "senderName" to me.name, "type" to type,
                "text" to text, "url" to url, "createdAt" to now
            )
        ).await()
        val preview = when (type) {
            "text", "sticker" -> text
            "image" -> "📷 صورة"
            "gif" -> "GIF"
            "video" -> "🎬 فيديو"
            "voice" -> "🎤 رسالة صوتية"
            else -> ""
        }
        chatRef.update(mapOf("lastMessage" to preview, "updatedAt" to now)).await()
    }

    suspend fun upload(chatId: String, uri: Uri): String {
        val ref = storage.reference.child("chats/$chatId/${UUID.randomUUID()}")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    // ---------- صلاحيات الإدارة ----------
    suspend fun setBadge(uid: String, badge: String) {
        db.collection("users").document(uid).update("badge", badge).await()
    }

    suspend fun setBanned(uid: String, banned: Boolean) {
        db.collection("users").document(uid).update("banned", banned).await()
    }

    /** ترقية لمدير = role admin + علامة ذهبية. والإزالة ترجعه مستخدم عادي */
    suspend fun setAdmin(uid: String, on: Boolean) {
        db.collection("users").document(uid)
            .update(mapOf("role" to if (on) "admin" else "user", "badge" to if (on) "gold" else "none")).await()
    }
}

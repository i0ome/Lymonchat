import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_database/firebase_database.dart';
import 'package:image_picker/image_picker.dart';
import 'package:record/record.dart';
import 'package:audioplayers/audioplayers.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  await Firebase.initializeApp(
    options: const FirebaseOptions(
      apiKey: "ضع_هنا_API_KEY_الحقيقي",
      appId: "ضع_هنا_APP_ID_الحقيقي",
      messagingSenderId: "ضع_هنا_SENDER_ID_الحقيقي",
      projectId: "cinemazone-ba49d",
      databaseURL: "https://cinemazone-ba49d-default-rtdb.europe-west1.firebasedatabase.app",
    ),
  );

  runApp(const LymonApp());
}

class LymonApp extends StatelessWidget {
  const LymonApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Lymon Chat',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: const Color(0xFF080C14),
        cardColor: const Color(0xFF121927),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFA3E635),
          secondary: Color(0xFFF59E0B),
        ),
      ),
      home: const AuthLockScreen(),
    );
  }
}

class AuthLockScreen extends StatefulWidget {
  const AuthLockScreen({Key? key}) : super(key: key);

  @override
  _AuthLockScreenState createState() => _AuthLockScreenState();
}

class _AuthLockScreenState extends State<AuthLockScreen> {
  final TextEditingController _usernameController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  bool isRegister = false;

  void _login() {
    String username = _usernameController.text.trim();
    String password = _passwordController.text.trim();

    if (username.isEmpty || password.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('يرجى كتابة اسم المستخدم وكلمة المرور')),
      );
      return;
    }

    FirebaseDatabase.instance.ref('users/$username').update({
      'username': username,
      'lastSeen': ServerValue.timestamp,
    });

    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (context) => MainChatScreen(currentUsername: username)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24.0),
          child: Container(
            padding: const EdgeInsets.all(24.0),
            decoration: BoxDecoration(
              color: const Color(0xFF121927),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: const Color(0xFF1E293B)),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                RichText(
                  text: const TextSpan(
                    style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold, color: Colors.white),
                    children: [
                      TextSpan(text: 'lym'),
                      TextSpan(text: 'o', style: TextStyle(color: Color(0xFFA3E635))),
                      TextSpan(text: 'n Chat'),
                    ],
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  isRegister ? 'إنشاء حساب جديد' : 'تسجيل الدخول للمتابعة',
                  style: const TextStyle(color: Colors.white54, fontSize: 14),
                ),
                const SizedBox(height: 20),
                TextField(
                  controller: _usernameController,
                  decoration: const InputDecoration(labelText: 'اسم المستخدم', border: OutlineInputBorder()),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _passwordController,
                  obscureText: true,
                  decoration: const InputDecoration(labelText: 'كلمة المرور', border: OutlineInputBorder()),
                ),
                const SizedBox(height: 20),
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFFA3E635),
                      foregroundColor: Colors.black,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                    onPressed: _login,
                    child: const Text('دخول إلى الشات 🚀', style: TextStyle(fontWeight: FontWeight.bold)),
                  ),
                ),
                const SizedBox(height: 12),
                GestureDetector(
                  onTap: () => setState(() => isRegister = !isRegister),
                  child: Text(
                    isRegister ? 'لديك حساب بالفعل؟ تسجيل الدخول' : 'ليس لديك حساب؟ إنشاء حساب جديد',
                    style: const TextStyle(color: Color(0xFFA3E635), fontSize: 13),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class MainChatScreen extends StatefulWidget {
  final String currentUsername;
  const MainChatScreen({Key? key, required this.currentUsername}) : super(key: key);

  @override
  _MainChatScreenState createState() => _MainChatScreenState();
}

class _MainChatScreenState extends State<MainChatScreen> {
  int _currentTab = 0;
  final TextEditingController _searchController = TextEditingController();
  String? searchResultUser;

  void _searchUser() async {
    String q = _searchController.text.trim();
    if (q.isEmpty) return;

    DataSnapshot snap = await FirebaseDatabase.instance.ref('users/$q').get();
    setState(() {
      if (snap.exists) {
        searchResultUser = q;
      } else {
        searchResultUser = null;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('لم يتم العثور على حساب بهذا الاسم')),
        );
      }
    });
  }

  void _openChat(String targetName, bool isGroup) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => ConversationScreen(
          currentUsername: widget.currentUsername,
          targetName: targetName,
          isGroup: isGroup,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('lymOn Chat', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: const Color(0xFF121927),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(48),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              TextButton(
                onPressed: () => setState(() => _currentTab = 0),
                child: Text('خاص', style: TextStyle(color: _currentTab == 0 ? const Color(0xFFA3E635) : Colors.white54, fontWeight: FontWeight.bold)),
              ),
              TextButton(
                onPressed: () => setState(() => _currentTab = 1),
                child: Text('قروبات', style: TextStyle(color: _currentTab == 1 ? const Color(0xFFA3E635) : Colors.white54, fontWeight: FontWeight.bold)),
              ),
            ],
          ),
        ),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _searchController,
                    decoration: const InputDecoration(
                      hintText: '🔍 ابحث عن اسم مستخدم...',
                      border: OutlineInputBorder(),
                      contentPadding: EdgeInsets.symmetric(horizontal: 12),
                    ),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.search, color: Color(0xFFA3E635)),
                  onPressed: _searchUser,
                ),
              ],
            ),
            if (searchResultUser != null) ...[
              const SizedBox(height: 10),
              ListTile(
                tileColor: const Color(0xFF121927),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                leading: const CircleAvatar(backgroundColor: Color(0xFFA3E635), child: Icon(Icons.person, color: Colors.black)),
                title: Text(searchResultUser!),
                subtitle: const Text('انقر لبدء المحادثة', style: TextStyle(color: Color(0xFFA3E635), fontSize: 12)),
                onTap: () => _openChat(searchResultUser!, false),
              ),
            ],
            const SizedBox(height: 16),
            Expanded(
              child: _currentTab == 0
                  ? StreamBuilder(
                      stream: FirebaseDatabase.instance.ref('chats/${widget.currentUsername}').onValue,
                      builder: (context, AsyncSnapshot<DatabaseEvent> snapshot) {
                        if (!snapshot.hasData || snapshot.data!.snapshot.value == null) {
                          return const Center(child: Text('لا توجد محادثات خاصة بعد', style: TextStyle(color: Colors.white54)));
                        }
                        Map<dynamic, dynamic> chats = snapshot.data!.snapshot.value as Map<dynamic, dynamic>;
                        List<String> targets = chats.keys.cast<String>().toList();

                        return ListView.builder(
                          itemCount: targets.length,
                          itemBuilder: (context, index) {
                            return Card(
                              color: const Color(0xFF121927),
                              child: ListTile(
                                leading: const CircleAvatar(child: Icon(Icons.person)),
                                title: Text(targets[index]),
                                subtitle: const Text('محادثة خاصة متصلة لحظياً مع الويب'),
                                onTap: () => _openChat(targets[index], false),
                              ),
                            );
                          },
                        );
                      },
                    )
                  : StreamBuilder(
                      stream: FirebaseDatabase.instance.ref('groups').onValue,
                      builder: (context, AsyncSnapshot<DatabaseEvent> snapshot) {
                        if (!snapshot.hasData || snapshot.data!.snapshot.value == null) {
                          return const Center(child: Text('لا توجد قروبات حالياً', style: TextStyle(color: Colors.white54)));
                        }
                        Map<dynamic, dynamic> groups = snapshot.data!.snapshot.value as Map<dynamic, dynamic>;

                        return ListView(
                          children: groups.entries.map((e) {
                            return Card(
                              color: const Color(0xFF121927),
                              child: ListTile(
                                leading: const CircleAvatar(backgroundColor: Colors.blue, child: Icon(Icons.group, color: Colors.white)),
                                title: Text(e.value['name'] ?? 'قروب'),
                                subtitle: const Text('قروب جماعي'),
                                onTap: () => _openChat(e.key, true),
                              ),
                            );
                          }).toList(),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class ConversationScreen extends StatefulWidget {
  final String currentUsername;
  final String targetName;
  final bool isGroup;

  const ConversationScreen({
    Key? key,
    required this.currentUsername,
    required this.targetName,
    required this.isGroup,
  }) : super(key: key);

  @override
  _ConversationScreenState createState() => _ConversationScreenState();
}

class _ConversationScreenState extends State<ConversationScreen> {
  final TextEditingController _msgController = TextEditingController();
  final AudioRecorder _audioRecorder = AudioRecorder();
  final AudioPlayer _audioPlayer = AudioPlayer();
  bool isRecording = false;

  String get _chatPath {
    if (widget.isGroup) {
      return 'group_messages/${widget.targetName}';
    } else {
      List<String> ids = [widget.currentUsername, widget.targetName]..sort();
      return 'messages/${ids[0]}_${ids[1]}';
    }
  }

  void _sendMessage({required String type, required String content}) {
    DatabaseReference ref = FirebaseDatabase.instance.ref(_chatPath).push();
    ref.set({
      'sender': widget.currentUsername,
      'type': type,
      if (type == 'text') 'text': content,
      if (type != 'text') 'mediaUrl': content,
      'timestamp': ServerValue.timestamp,
    });

    if (!widget.isGroup) {
      FirebaseDatabase.instance.ref('chats/${widget.currentUsername}/${widget.targetName}').set(true);
      FirebaseDatabase.instance.ref('chats/${widget.targetName}/${widget.currentUsername}').set(true);
    }
  }

  void _pickImage() async {
    final ImagePicker picker = ImagePicker();
    final XFile? image = await picker.pickImage(source: ImageSource.gallery, imageQuality: 50);
    if (image != null) {
      List<int> bytes = await File(image.path).readAsBytes();
      String base64Image = "data:image/png;base64,${base64Encode(bytes)}";
      _sendMessage(type: 'image', content: base64Image);
    }
  }

  void _toggleRecording() async {
    if (isRecording) {
      final path = await _audioRecorder.stop();
      if (path != null) {
        List<int> bytes = await File(path).readAsBytes();
        String base64Audio = "data:audio/webm;base64,${base64Encode(bytes)}";
        _sendMessage(type: 'audio', content: base64Audio);
      }
      setState(() => isRecording = false);
    } else {
      if (await _audioRecorder.hasPermission()) {
        await _audioRecorder.start(const RecordConfig(), path: '');
        setState(() => isRecording = true);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.targetName),
        backgroundColor: const Color(0xFF121927),
      ),
      body: Column(
        children: [
          Expanded(
            child: StreamBuilder(
              stream: FirebaseDatabase.instance.ref(_chatPath).onValue,
              builder: (context, AsyncSnapshot<DatabaseEvent> snapshot) {
                if (!snapshot.hasData || snapshot.data!.snapshot.value == null) {
                  return const Center(child: Text('اكتب أول رسالة للبدء...', style: TextStyle(color: Colors.white54)));
                }

                Map<dynamic, dynamic> msgs = snapshot.data!.snapshot.value as Map<dynamic, dynamic>;
                var sortedKeys = msgs.keys.toList()..sort();

                return ListView.builder(
                  itemCount: sortedKeys.length,
                  itemBuilder: (context, index) {
                    var m = msgs[sortedKeys[index]];
                    bool isMe = m['sender'] == widget.currentUsername;

                    return Align(
                      alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
                      child: Container(
                        margin: const EdgeInsets.symmetric(vertical: 4, horizontal: 12),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: isMe ? const Color(0xFFA3E635) : const Color(0xFF121927),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            if (widget.isGroup && !isMe)
                              Text(m['sender'] ?? '', style: const TextStyle(color: Colors.blueAccent, fontSize: 10, fontWeight: FontWeight.bold)),
                            _buildMessageContent(m, isMe),
                          ],
                        ),
                      ),
                    );
                  },
                );
              },
            ),
          ),
          Container(
            padding: const EdgeInsets.all(8),
            color: const Color(0xFF121927),
            child: Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.image, color: Colors.white),
                  onPressed: _pickImage,
                ),
                IconButton(
                  icon: Icon(Icons.mic, color: isRecording ? Colors.red : Colors.white),
                  onPressed: _toggleRecording,
                ),
                Expanded(
                  child: TextField(
                    controller: _msgController,
                    decoration: const InputDecoration(
                      hintText: 'اكتب رسالتك...',
                      border: InputBorder.none,
                    ),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.send, color: Color(0xFFA3E635)),
                  onPressed: () {
                    if (_msgController.text.trim().isNotEmpty) {
                      _sendMessage(type: 'text', content: _msgController.text.trim());
                      _msgController.clear();
                    }
                  },
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMessageContent(Map<dynamic, dynamic> m, bool isMe) {
    Color textColor = isMe ? Colors.black : Colors.white;
    String type = m['type'] ?? 'text';

    if (type == 'text') {
      return Text(m['text'] ?? '', style: TextStyle(color: textColor, fontSize: 15));
    } else if (type == 'image') {
      String url = m['mediaUrl'] ?? '';
      if (url.startsWith('data:image')) {
        return Image.memory(base64Decode(url.split(',').last), width: 200, fit: BoxFit.cover);
      }
      return Image.network(url, width: 200, fit: BoxFit.cover);
    } else if (type == 'audio') {
      return IconButton(
        icon: Icon(Icons.play_arrow, color: textColor),
        onPressed: () async {
          String url = m['mediaUrl'] ?? '';
          if (url.startsWith('data:audio')) {
            await _audioPlayer.play(BytesSource(base64Decode(url.split(',').last)));
          } else {
            await _audioPlayer.play(UrlSource(url));
          }
        },
      );
    }
    return const SizedBox();
  }
}

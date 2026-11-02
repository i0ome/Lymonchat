// ============================================================
// Lymon — تطبيق مراسلة (Flutter / Dart) في ملف واحد
// الخادم: نفس Google Apps Script (backend/Code.gs) بدون أي تعديل.
//
// pubspec.yaml → dependencies:
//   http: ^1.2.2
//   shared_preferences: ^2.3.2
//   record: ^5.1.2
//   audioplayers: ^6.1.0
//   path_provider: ^2.1.4
//
// Android: في AndroidManifest.xml أضف
//   <uses-permission android:name="android.permission.INTERNET"/>
//   <uses-permission android:name="android.permission.RECORD_AUDIO"/>
// وفي android/app/build.gradle اجعل minSdkVersion 23 على الأقل.
// ============================================================

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// ضع هنا رابط Apps Script (ينتهي بـ /exec)
const String kApiUrl = 'https://script.google.com/macros/s/REPLACE_ME/exec';

const kBg = Color(0xFF0B141A);
const kS1 = Color(0xFF111B21);
const kS2 = Color(0xFF202C33);
const kAccent = Color(0xFF00A884);
const kGold = Color(0xFFFFC107);
const kBlue = Color(0xFF1DA1F2);
const kMine = Color(0xFF005C4B);

// ---------------------------------------------------------------- models
class Sess {
  final String token, user;
  final bool admin;
  Sess(this.token, this.user, this.admin);
}

class U {
  final String name, badge;
  final bool banned;
  U(this.name, this.badge, this.banned);
}

class M {
  final String id, from, to, type, body;
  final int ts;
  M(this.id, this.from, this.to, this.type, this.body, this.ts);
}

// ---------------------------------------------------------------- state/API
final session = ValueNotifier<Sess?>(null);
final navKey = GlobalKey<NavigatorState>();

Future<Map<String, dynamic>> _post(Map<String, dynamic> body) async {
  final client = http.Client();
  try {
    final req = http.Request('POST', Uri.parse(kApiUrl))
      ..followRedirects = false
      ..headers['Content-Type'] = 'text/plain;charset=utf-8'
      ..body = jsonEncode(body);
    var res = await http.Response.fromStream(
        await client.send(req).timeout(const Duration(seconds: 30)));
    // Apps Script يرد بتحويل 302، والنتيجة تُجلب بـ GET من العنوان الجديد
    if ({301, 302, 303, 307, 308}.contains(res.statusCode)) {
      final loc = res.headers['location'];
      if (loc == null) return {'ok': false, 'error': 'رد غير صالح'};
      res = await client.get(Uri.parse(loc)).timeout(const Duration(seconds: 60));
    }
    return jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  } catch (_) {
    return {'ok': false, 'error': 'تعذر الاتصال بالخادم'};
  } finally {
    client.close();
  }
}

Future<Map<String, dynamic>> call(String action, [Map<String, dynamic>? extra]) async {
  final r = await _post({
    'action': action,
    'token': session.value?.token ?? '',
    ...?extra,
  });
  if (r['ok'] != true && (r['error'] == 'unauthorized' || r['error'] == 'banned')) {
    if (session.value != null) await logout();
  }
  return r;
}

String errText(dynamic e) {
  if (e == 'banned') return 'تم حظر هذا الحساب';
  if (e == 'unauthorized') return 'انتهت الجلسة، سجّل الدخول من جديد';
  return '${e ?? 'خطأ'}';
}

Future<void> saveSession(Sess s) async {
  final p = await SharedPreferences.getInstance();
  await p.setString('token', s.token);
  await p.setString('user', s.user);
  await p.setBool('admin', s.admin);
  session.value = s;
}

Future<void> logout() async {
  final p = await SharedPreferences.getInstance();
  await p.clear();
  session.value = null;
  navKey.currentState?.popUntil((r) => r.isFirst);
}

// ---------------------------------------------------------------- app
void main() => runApp(const LymonApp());

class LymonApp extends StatefulWidget {
  const LymonApp({super.key});
  @override
  State<LymonApp> createState() => _LymonAppState();
}

class _LymonAppState extends State<LymonApp> {
  bool loaded = false;

  @override
  void initState() {
    super.initState();
    SharedPreferences.getInstance().then((p) {
      final t = p.getString('token');
      if (t != null) {
        session.value = Sess(t, p.getString('user') ?? '', p.getBool('admin') ?? false);
      }
      setState(() => loaded = true);
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Lymon',
      debugShowCheckedModeBanner: false,
      navigatorKey: navKey,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: kBg,
        colorScheme: const ColorScheme.dark(primary: kAccent, surface: kS1),
        appBarTheme: const AppBarTheme(backgroundColor: kS1),
        drawerTheme: const DrawerThemeData(backgroundColor: kS1),
      ),
      builder: (c, child) => Directionality(textDirection: TextDirection.rtl, child: child!),
      home: !loaded
          ? const Scaffold(body: Center(child: CircularProgressIndicator()))
          : ValueListenableBuilder<Sess?>(
              valueListenable: session,
              builder: (_, s, __) => s == null ? const AuthPage() : HomePage(key: ValueKey(s.token)),
            ),
    );
  }
}

class VerifiedBadge extends StatelessWidget {
  final String badge;
  const VerifiedBadge(this.badge, {super.key});
  @override
  Widget build(BuildContext context) => badge.isEmpty
      ? const SizedBox.shrink()
      : Padding(
          padding: const EdgeInsetsDirectional.only(start: 4),
          child: Icon(Icons.verified, size: 18, color: badge == 'gold' ? kGold : kBlue),
        );
}

// ---------------------------------------------------------------- auth
class AuthPage extends StatefulWidget {
  const AuthPage({super.key});
  @override
  State<AuthPage> createState() => _AuthPageState();
}

class _AuthPageState extends State<AuthPage> {
  final u = TextEditingController(), p = TextEditingController();
  bool reg = false, busy = false;
  String? err;

  Future<void> submit() async {
    setState(() { busy = true; err = null; });
    final r = await call(reg ? 'register' : 'login', {'username': u.text.trim(), 'password': p.text});
    if (!mounted) return;
    if (r['ok'] == true) {
      await saveSession(Sess(r['token'], r['username'], r['admin'] == true));
    } else {
      setState(() { err = errText(r['error']); busy = false; });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(28),
          child: Column(children: [
            const Text('Lymon', style: TextStyle(fontSize: 40, fontWeight: FontWeight.bold, color: kAccent)),
            const SizedBox(height: 4),
            Text(reg ? 'إنشاء حساب جديد' : 'تسجيل الدخول', style: const TextStyle(color: Colors.grey)),
            const SizedBox(height: 28),
            TextField(controller: u, decoration: const InputDecoration(labelText: 'اسم المستخدم', border: OutlineInputBorder())),
            const SizedBox(height: 12),
            TextField(controller: p, obscureText: true, decoration: const InputDecoration(labelText: 'كلمة المرور', border: OutlineInputBorder())),
            if (err != null) Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Text(err!, style: const TextStyle(color: Colors.redAccent)),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity, height: 50,
              child: FilledButton(
                onPressed: busy ? null : submit,
                child: busy
                    ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2))
                    : Text(reg ? 'إنشاء الحساب' : 'دخول'),
              ),
            ),
            TextButton(
              onPressed: () => setState(() { reg = !reg; err = null; }),
              child: Text(reg ? 'لدي حساب بالفعل' : 'ليس لدي حساب؟ سجّل الآن'),
            ),
          ]),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------- home
class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  List<U> users = [];
  String tab = 'chats';
  bool loading = true;

  @override
  void initState() { super.initState(); load(); }

  Future<void> load() async {
    final r = await call('users');
    if (!mounted) return;
    if (r['ok'] == true) {
      users = (r['users'] as List)
          .map((o) => U(o['username'], (o['badge'] ?? '') as String, o['banned'] == true))
          .toList();
    } else {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(errText(r['error']))));
    }
    setState(() => loading = false);
  }

  Future<void> adminOp(String op, String name) async {
    final r = await call(op, {'target': name});
    if (r['ok'] != true && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(errText(r['error']))));
    }
    load();
  }

  Widget avatar(String n) => CircleAvatar(
        backgroundColor: kS2,
        child: Text(n.isEmpty ? '?' : n[0].toUpperCase(), style: const TextStyle(color: kAccent, fontWeight: FontWeight.bold)),
      );

  @override
  Widget build(BuildContext context) {
    final s = session.value!;
    final isAdminTab = tab == 'admin' && s.admin;
    return Scaffold(
      appBar: AppBar(
        title: Text(isAdminTab ? 'لوحة الإشراف' : 'Lymon', style: const TextStyle(fontWeight: FontWeight.bold)),
        actions: [IconButton(icon: const Icon(Icons.refresh), onPressed: load)],
      ),
      drawer: Drawer(
        child: SafeArea(
          child: ListView(children: [
            Padding(
              padding: const EdgeInsets.all(24),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('Lymon', style: TextStyle(fontSize: 28, color: kAccent, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                Row(children: [
                  Text(s.user, style: const TextStyle(fontWeight: FontWeight.bold)),
                  if (s.admin) const VerifiedBadge('gold'),
                ]),
              ]),
            ),
            const Divider(),
            ListTile(
              leading: const Icon(Icons.chat), title: const Text('المحادثات'),
              selected: tab == 'chats',
              onTap: () { setState(() => tab = 'chats'); Navigator.pop(context); },
            ),
            // يظهر فقط إذا أكّد الخادم أن الجلسة جلسة مشرف
            if (s.admin) ListTile(
              leading: const Icon(Icons.admin_panel_settings, color: kGold), title: const Text('لوحة الإشراف'),
              selected: tab == 'admin',
              onTap: () { setState(() => tab = 'admin'); Navigator.pop(context); load(); },
            ),
            ListTile(
              leading: const Icon(Icons.logout), title: const Text('تسجيل الخروج'),
              onTap: logout,
            ),
          ]),
        ),
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : isAdminTab ? adminList() : chatList(),
    );
  }

  Widget chatList() {
    final list = users.where((u) => !u.banned).toList();
    if (list.isEmpty) return const Center(child: Text('لا يوجد مستخدمون بعد', style: TextStyle(color: Colors.grey)));
    return RefreshIndicator(
      onRefresh: load,
      child: ListView.separated(
        itemCount: list.length,
        separatorBuilder: (_, __) => const Divider(height: 1, color: kS2),
        itemBuilder: (_, i) {
          final u = list[i];
          return ListTile(
            leading: avatar(u.name),
            title: Row(children: [Text(u.name), VerifiedBadge(u.badge)]),
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => ChatPage(peer: u))),
          );
        },
      ),
    );
  }

  Widget adminList() {
    final list = users.where((u) => u.badge != 'gold').toList();
    if (list.isEmpty) return const Center(child: Text('لا يوجد مستخدمون', style: TextStyle(color: Colors.grey)));
    return ListView.separated(
      itemCount: list.length,
      separatorBuilder: (_, __) => const Divider(height: 1, color: kS2),
      itemBuilder: (_, i) {
        final u = list[i];
        return Padding(
          padding: const EdgeInsets.all(12),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              avatar(u.name), const SizedBox(width: 12),
              Text(u.name, style: const TextStyle(fontWeight: FontWeight.w600)),
              VerifiedBadge(u.badge),
              if (u.banned) const Padding(
                padding: EdgeInsetsDirectional.only(start: 8),
                child: Text('محظور', style: TextStyle(color: Colors.redAccent)),
              ),
            ]),
            const SizedBox(height: 8),
            Wrap(spacing: 8, children: [
              OutlinedButton(
                onPressed: () => adminOp(u.banned ? 'unban' : 'ban', u.name),
                child: Text(u.banned ? 'فك الحظر' : 'حظر'),
              ),
              OutlinedButton(
                onPressed: () => adminOp(u.badge == 'blue' ? 'unverify' : 'verify', u.name),
                child: Text(u.badge == 'blue' ? 'إلغاء التوثيق' : 'توثيق ✓'),
              ),
            ]),
          ]),
        );
      },
    );
  }
}

// ---------------------------------------------------------------- chat
class ChatPage extends StatefulWidget {
  final U peer;
  const ChatPage({super.key, required this.peer});
  @override
  State<ChatPage> createState() => _ChatPageState();
}

class _ChatPageState extends State<ChatPage> {
  final msgs = <M>[];
  final ctrl = TextEditingController();
  final scroll = ScrollController();
  final rec = AudioRecorder();
  final player = AudioPlayer();
  Timer? timer;
  int since = 0;
  bool recording = false;

  @override
  void initState() {
    super.initState();
    poll();
    timer = Timer.periodic(const Duration(seconds: 3), (_) => poll());
  }

  @override
  void dispose() {
    timer?.cancel();
    ctrl.dispose();
    scroll.dispose();
    rec.dispose();
    player.dispose();
    super.dispose();
  }

  Future<void> poll() async {
    final r = await call('poll', {'peer': widget.peer.name, 'since': since});
    if (!mounted || r['ok'] != true) return;
    final known = msgs.map((m) => m.id).toSet();
    final fresh = (r['messages'] as List)
        .map((o) => M(o['id'], o['from'], o['to'], o['type'], '${o['body']}', (o['ts'] as num).toInt()))
        .where((m) => !known.contains(m.id))
        .toList();
    if (fresh.isEmpty) return;
    setState(() {
      msgs.addAll(fresh);
      since = msgs.map((m) => m.ts).reduce((a, b) => a > b ? a : b);
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (scroll.hasClients) scroll.animateTo(scroll.position.maxScrollExtent, duration: const Duration(milliseconds: 200), curve: Curves.easeOut);
    });
  }

  void toast(String t) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(t)));

  Future<void> send(String type, String body) async {
    final r = await call('send', {'to': widget.peer.name, 'type': type, 'body': body});
    if (r['ok'] != true) { if (mounted) toast(errText(r['error'])); return; }
    poll();
  }

  Future<void> onAction() async {
    if (recording) {
      final path = await rec.stop();
      setState(() => recording = false);
      if (path == null) return;
      final bytes = await File(path).readAsBytes();
      if (bytes.length > 3000000) { toast('التسجيل كبير جداً'); return; }
      await send('voice', base64Encode(bytes));
      File(path).delete().catchError((_) => File(path));
    } else if (ctrl.text.trim().isNotEmpty) {
      final t = ctrl.text.trim();
      ctrl.clear();
      setState(() {});
      await send('text', t);
    } else {
      if (!await rec.hasPermission()) { toast('اسمح بالوصول للمايكروفون'); return; }
      final dir = await getTemporaryDirectory();
      final path = '${dir.path}/rec_${DateTime.now().millisecondsSinceEpoch}.m4a';
      await rec.start(const RecordConfig(encoder: AudioEncoder.aacLc, bitRate: 32000, sampleRate: 16000), path: path);
      setState(() => recording = true);
    }
  }

  Future<void> play(M m) async {
    final r = await call('media', {'id': m.id});
    if (r['ok'] != true) { if (mounted) toast(errText(r['error'])); return; }
    final dir = await getTemporaryDirectory();
    final f = File('${dir.path}/play_${m.id}.m4a');
    await f.writeAsBytes(base64Decode(r['data']));
    await player.stop();
    await player.play(DeviceFileSource(f.path));
  }

  @override
  Widget build(BuildContext context) {
    final me = session.value?.user ?? '';
    return Scaffold(
      appBar: AppBar(title: Row(children: [Text(widget.peer.name), VerifiedBadge(widget.peer.badge)])),
      body: Column(children: [
        Expanded(
          child: ListView.builder(
            controller: scroll,
            padding: const EdgeInsets.all(10),
            itemCount: msgs.length,
            itemBuilder: (_, i) {
              final m = msgs[i];
              final mine = m.from == me;
              return Align(
                alignment: mine ? AlignmentDirectional.centerEnd : AlignmentDirectional.centerStart,
                child: Container(
                  margin: const EdgeInsets.symmetric(vertical: 3),
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  constraints: const BoxConstraints(maxWidth: 280),
                  decoration: BoxDecoration(color: mine ? kMine : kS2, borderRadius: BorderRadius.circular(14)),
                  child: m.type == 'voice'
                      ? Row(mainAxisSize: MainAxisSize.min, children: [
                          InkWell(onTap: () => play(m), child: const Icon(Icons.play_arrow)),
                          const SizedBox(width: 6),
                          const Text('رسالة صوتية'),
                        ])
                      : Text(m.body),
                ),
              );
            },
          ),
        ),
        Container(
          color: kS1,
          padding: const EdgeInsets.all(8),
          child: SafeArea(
            top: false,
            child: Row(children: [
              Expanded(
                child: recording
                    ? const Padding(
                        padding: EdgeInsets.all(12),
                        child: Text('● جارٍ التسجيل… اضغط للإرسال', style: TextStyle(color: Colors.redAccent)),
                      )
                    : TextField(
                        controller: ctrl,
                        onChanged: (_) => setState(() {}),
                        minLines: 1, maxLines: 4,
                        decoration: InputDecoration(
                          hintText: 'اكتب رسالة',
                          filled: true, fillColor: kS2,
                          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(24), borderSide: BorderSide.none),
                        ),
                      ),
              ),
              const SizedBox(width: 8),
              CircleAvatar(
                backgroundColor: kAccent,
                child: IconButton(
                  color: Colors.white,
                  icon: Icon(recording || ctrl.text.trim().isNotEmpty ? Icons.send : Icons.mic),
                  onPressed: onAction,
                ),
              ),
            ]),
          ),
        ),
      ]),
    );
  }
}
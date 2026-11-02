# Lymon chat

تطبيق مراسلة أندرويد (Kotlin + Jetpack Compose + Firebase).

## خطوات التشغيل
1. أنشئ مشروع في https://console.firebase.google.com
2. أضف تطبيق Android بالاسم `com.lymon.chat` وأضف بصمة SHA-1 (من Android Studio: Gradle > app > Tasks > android > signingReport)
3. نزّل `google-services.json` وضعه داخل مجلد `app/`
4. فعّل Authentication: Email/Password و Google
5. فعّل Firestore Database و Storage
6. الصق محتوى `firestore.rules` في Firestore > Rules ثم Publish، و`storage.rules` في Storage > Rules ثم Publish
7. افتح المجلد في Android Studio وانتظر Sync
8. Build > Build Bundle(s) / APK(s) > Build APK(s)

## أول حساب إداري
سجّل حساب في التطبيق، ثم افتح Firestore > users > (مستندك) وعدّل:
- role = admin
- badge = gold
بعدها تظهر لك لوحة الإدارة (أيقونة الدرع) وتقدر ترقّي مدراء جدد وتوثّق بالأزرق وتحظر.

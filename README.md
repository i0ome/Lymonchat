# Lymon (Flutter)

## الخادم
انشر `backend/Code.gs` في Google Apps Script (Web app: Execute as Me, Access: Anyone)،
شغّل `setupAdmin()` مرة واحدة ثم احذف كلمة السر من الكود. انسخ رابط `/exec`.

## التطبيق
1. افتح `lib/main.dart` وغيّر `kApiUrl` إلى رابط `/exec`.
2. داخل هذا المجلد شغّل: `bash setup.sh`
   (يولّد android/ios ويضيف الصلاحيات ويضبط minSdk ويجلب الحزم).
3. التشغيل: `flutter run` — بناء APK: `flutter build apk --release`
   الناتج: build/app/outputs/flutter-apk/app-release.apk

## يدوياً بدون setup.sh
- `flutter create --project-name lymon --org com.lymon .` ثم `flutter pub get`
- أضف في AndroidManifest.xml: INTERNET و RECORD_AUDIO
- اجعل minSdk = 23 في android/app/build.gradle(.kts)

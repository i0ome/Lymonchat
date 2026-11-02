#!/usr/bin/env bash
# يولّد مجلدات android/ios ويضبط الصلاحيات تلقائياً. شغّله مرة واحدة: bash setup.sh
set -e
command -v flutter >/dev/null || { echo "Flutter غير مثبت: https://docs.flutter.dev/get-started/install"; exit 1; }

flutter create --project-name lymon --org com.lymon --platforms android,ios .

# صلاحيات أندرويد
M=android/app/src/main/AndroidManifest.xml
if ! grep -q RECORD_AUDIO "$M"; then
  sed -i.bak 's|<application|<uses-permission android:name="android.permission.INTERNET"/>\n    <uses-permission android:name="android.permission.RECORD_AUDIO"/>\n    <application|' "$M" && rm -f "$M.bak"
fi

# minSdk 23 (مطلوب لمكتبة record)
for G in android/app/build.gradle.kts android/app/build.gradle; do
  [ -f "$G" ] && sed -i.bak -E 's/minSdk(Version)? ?=? ?flutter\.minSdkVersion/minSdk = 23/; s/minSdkVersion flutter\.minSdkVersion/minSdkVersion 23/' "$G" && rm -f "$G.bak"
done

# iOS: وصف استخدام المايكروفون
P=ios/Runner/Info.plist
if [ -f "$P" ] && ! grep -q NSMicrophoneUsageDescription "$P"; then
  sed -i.bak 's|</dict>|\t<key>NSMicrophoneUsageDescription</key>\n\t<string>لتسجيل الرسائل الصوتية</string>\n</dict>|' "$P" && rm -f "$P.bak"
fi

flutter pub get
echo "تم. عدّل kApiUrl في lib/main.dart ثم: flutter run   أو   flutter build apk"

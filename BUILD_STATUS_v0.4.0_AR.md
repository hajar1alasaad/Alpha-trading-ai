# حالة البناء — v0.4.0

## ما أضيف
- طبقة `AndroidSecureVault` لتشفير القيم محلياً بـ AES-256-GCM.
- إنشاء مفتاح التشفير داخل Android Keystore.
- مصادقة اسم السجل عبر AAD.
- تعطيل Android Auto Backup لتقليل خطر نسخ بيانات مشفرة إلى جهاز لا يملك مفتاحها.
- رفع رقم الإصدار إلى 0.4.0 / versionCode 4.

## ما لم يكتمل
- Phantom connect/deeplink crypto handshake.
- Sign Message وsignature verification.
- حفظ جلسة Phantom الفعلية.
- Solana RPC/Jupiter quote/swap/sign/send.
- محفظة تداول مستقلة.
- وكلاء AI فعليون أو تنفيذ تلقائي.

## الاختبار
لم يتم بناء APK في بيئة العمل هذه لعدم توفر Gradle/Android SDK مثبتين هنا. يجب تشغيل CI على GitHub أو البناء محلياً قبل توزيع أي APK. لا تعتبر هذه النسخة صالحة لتداول الأموال الحقيقية.

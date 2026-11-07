# ALPHA TRADE AI — Android

تطبيق Android أولي لتحليل الأسواق، مع تكامل Phantom Solana Deep Links قيد الاختبار.

## الإصدار الحالي 0.5.0
- لوحة تحليل مؤشرات سوقية تعليمية.
- ربط Phantom عبر Universal Deep Links.
- طلب توقيع رسالة إثبات ملكية والتحقق من توقيع Ed25519.
- تخزين بيانات جلسة التطبيق المشفرة محلياً باستخدام Android Keystore.

## إعداد رابط تعريف التطبيق
قبل استخدام زر ربط Phantom، مرّر رابط HTTPS عام لصفحة تعريف التطبيق:

```bash
gradle assembleDebug -PPHANTOM_APP_URL=https://YOUR-PUBLIC-APP-PAGE.example
```

بدون هذا الرابط، لن يبدأ الاتصال. لا تستخدم رابطاً تجريبياً عند التوزيع.

## بناء APK
يتطلب Android SDK وJDK 17 وGradle 8.9:

```bash
gradle testDebugUnitTest assembleDebug -PPHANTOM_APP_URL=https://YOUR-PUBLIC-APP-PAGE.example
```

APK الناتج (بعد نجاح البناء): `app/build/outputs/apk/debug/app-debug.apk`.

## تنبيه مهم
هذا ليس بوت تداول مكتمل. لا توجد محفظة تداول منفصلة، ولا Jupiter swaps، ولا تنفيذ صفقات حقيقية. لا تدخل عبارة استرداد Phantom أو مفاتيح المحفظة الأساسية. لا تموّل محفظة للتداول عبر هذه النسخة.

راجع `docs/PHANTOM_INTEGRATION_v0.5.0_AR.md` و`docs/BUILD_STATUS_v0.5.0_AR.md` قبل الاختبار.

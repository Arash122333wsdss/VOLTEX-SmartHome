# VOLTEX Smart Home — Redesign UI build

این نسخه بر پایه سورس VOLTEX Smart Home v3 ارسالی ساخته شده و تمرکز آن بازطراحی رابط کاربری و آیکون برنامه است.

## تغییرات رابط کاربری
- تم مشکی/ذغالی با تأکید قرمز و طلایی VOLTEX
- صفحه ورود بازطراحی‌شده و کارت مدرن
- هدر برنددار با لوگوی برنامه
- کارت‌های اتاق و کنترل خروجی‌ها با کنتراست و حاشیه‌های جدید
- آیکون Launcher با لوگوی ارسالی

## بخش‌هایی که عمداً تغییر داده نشده‌اند
- Package/Application ID: `com.voltex.smarthome`
- زبان و ساختار Native Kotlin
- احراز هویت HTTP Basic با نام کاربری ثابت `admin`
- مسیرهای API: `/api/state`, `/api/set`, `/api/all`, `/api/settings`
- فاصله دریافت وضعیت ۳ ثانیه
- رمزنگاری AES-GCM با Android Keystore
- منطق تغییر رمز پنل و تنظیم IP

## ساخت APK با GitHub Actions
1. محتوای این پوشه را در ریپازیتوری GitHub قرار بده.
2. از تب Actions، ورک‌فلو **Build VOLTEX APK** را اجرا کن (یا روی main/master پوش کن).
3. پس از موفقیت، Artifact با نام `VOLTEX-SmartHome-debug-apk` را دانلود کن.

## نکته مهم درباره تست
در محیط آماده‌سازی این فایل، Gradle و Android SDK نصب نبودند؛ بنابراین APK در اینجا Build یا روی ESP32 واقعی تست نشده است. ورک‌فلو GitHub موجود است و باید Build نهایی را انجام دهد. قبل از استفاده روزمره، APK را روی گوشی و کنترلر واقعی آزمایش کن.

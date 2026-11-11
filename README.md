# Befrest (بفرست)

اپ اندرویدی انتقال فایل بین گوشی و Android TV روی یک شبکه Wi-Fi مشترک.

## ساخت APK با GitHub Actions
1. پروژه را در ریشه یک مخزن GitHub قرار دهید.
2. از زبانه Actions اجرای workflow با نام **Build APK** را انجام دهید یا یک commit جدید push کنید.
3. پس از پایان موفق، از صفحه اجرای workflow بخش **Artifacts** فایل `Befrest-debug-apk` را دانلود کنید.
4. فایل APK داخل ZIP آرتیفکت قرار دارد.

## استخراج ZIP در ریشه مخزن
فایل ZIP پروژه را در ریشه مخزن قرار دهید و commit/push کنید. workflow با نام **Extract project ZIP** آن را در ریشه استخراج و commit می‌کند. نام فایل ورودی باید `Befrest-Source.zip` باشد.

## نیازمندی‌ها
- Android Studio جدید
- JDK 17
- Android SDK 35
- حداقل Android 9 / API 28

## وضعیت
این نسخه اسکلت MVP است و قبل از انتشار عمومی باید روی گوشی و Android TV تست و بخش‌های انتقال/کشف دستگاه تکمیل شوند.

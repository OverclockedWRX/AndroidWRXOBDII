# WRX Race HUD — keep rules added when minify is enabled.

# Android Auto / Car App Library: service, session and screens are instantiated
# by the host via the manifest and by reflection-based template serialisation.
-keep class * extends androidx.car.app.CarAppService
-keep class * extends androidx.car.app.Session
-keep class * extends androidx.car.app.Screen
-keep class androidx.car.app.** { *; }
-dontwarn androidx.car.app.**

# usb-serial-for-android: drivers are looked up through the probe table.
-keep class com.hoho.android.usbserial.** { *; }
-dontwarn com.hoho.android.usbserial.**

# DataStore (protobuf-lite based preferences).
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable

# Release: strip verbose/debug logging (ObdConnection logs every command/reply preview at debug).
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

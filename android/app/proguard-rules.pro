# SafeRing ProGuard Rules

# Keep data classes for Gson/Room
-keepclassmembers class online.db1k.safering.android.data.** { *; }

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**

# Firebase Crashlytics + Analytics
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Logger references (used via reflection by Crashlytics)
-keep class online.db1k.safering.android.util.Logger { *; }

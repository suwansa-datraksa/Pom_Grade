# Pom Grade - ProGuard / R8 Rules
# จัดทำโดย ครูสุวรรณษา เดชรักษา

# Keep Line Numbers and Attributes for Debugging
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Room Database
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.school.omrscanner.data.local.** { *; }

# Moshi & Retrofit Models
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.school.omrscanner.sync.** { *; }
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# WorkManager
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class com.school.omrscanner.sync.SyncWorker { *; }

# OpenCV Native JNI
-keep class org.opencv.** { *; }
-keepclassmembers class org.opencv.** {
    native <methods>;
}

# UI & Custom Views
-keep class com.school.omrscanner.ui.** { *; }
-keepclassmembers class com.school.omrscanner.ui.** {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Google Play Services Auth
-keep class com.google.android.gms.auth.api.** { *; }
-keep class com.google.android.gms.common.** { *; }


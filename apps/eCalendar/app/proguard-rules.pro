-keep class net.fortuna.ical4j.**
-keep interface net.fortuna.ical4j.**
-keepattributes *Annotation*
-keep class * implements net.fortuna.ical4j.** { *; }
-dontwarn org.jparsec.**
-dontwarn org.codehaus.groovy.**
-dontwarn groovy.**
-dontwarn javax.cache.**
-dontwarn com.github.erosb.jsonsKema.**
-dontwarn java.beans.**

# Tink (EncryptedSharedPreferences)
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# WorkManager internal Room database (instantiated via reflection)
-keep class androidx.work.impl.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

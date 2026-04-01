# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# kotlinx.serialization
-keep class dev.equran.data.api.** { *; }
-keep class dev.equran.data.repository.QuranDataManager$* { *; }
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

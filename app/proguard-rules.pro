# LiteRT-LM - keep native inference classes
-keep class com.google.ai.edge.litertlm.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

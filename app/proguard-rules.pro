# Room (used inside WorkManager) creates its generated database class by reflection
# through the no-arg constructor. Without this, R8 removes the constructor and the
# release build crashes at startup in WorkManagerInitializer.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

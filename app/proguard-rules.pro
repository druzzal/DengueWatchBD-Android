# R8 rules for the release build.
#
# The build file has referenced this path since the project was created, but
# nothing read it while minification was off, so it never existed. Enabling R8
# to claw back the dex weight MapLibre brought with it is what surfaced that.

# WorkManager constructs its workers reflectively, from the class name recorded
# in its database when the work was enqueued. R8 has no way to see that call, so
# without these the background refresh and the weekly digest would be renamed or
# removed and would silently stop running — the failure mode being that alerts
# quietly never arrive, which is the worst way for this app to break.
-keep class com.denguewatch.bd.data.RefreshWorker { <init>(...); }
-keep class com.denguewatch.bd.data.WeeklyDigestWorker { <init>(...); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# MapLibre calls into its own native library across the JNI boundary, and JNI
# lookups are by name. Its own consumer rules cover most of this; these are the
# entry points that have historically needed pinning as well.
-keep class org.maplibre.android.** { *; }
-keep class org.maplibre.geojson.** { *; }
-dontwarn org.maplibre.**

# The feed is parsed with org.json against string keys, so no model class is
# reflected over — no keep rules are needed for the domain or wire types.

# Keep line numbers so a crash report from a release build can still be read.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit finds its own components by reading registrar class names out of the
# merged manifest and calling each one's no-arg constructor by reflection. R8
# cannot see that call, so it strips the constructors and component discovery
# fails — which takes the text recogniser with it, and with that the whole
# "scan a report" path.
#
# It fails quietly. The only sign is three ComponentDiscovery warnings at
# startup, and the release build is the only build that shows them: the debug
# build logs none, which is how this was found at all. Keep the constructors.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
-keep class com.google.mlkit.**.internal.*Registrar {
    <init>();
}
-dontwarn com.google.mlkit.**

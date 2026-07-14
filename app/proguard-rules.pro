# DataStore preferences bundles protobuf-lite, which reads message fields
# (value_, etc.) via reflection — R8 must not strip or rename them.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Shizuku: we call Shizuku.newProcess(...) reflectively, and the AIDL/binder
# classes are referenced by the platform — keep them intact.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }

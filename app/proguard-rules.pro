# DataStore preferences bundles protobuf-lite, which reads message fields
# (value_, etc.) via reflection — R8 must not strip or rename them.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# kotlinx.serialization: keep generated serializers of our @Serializable classes (saved games and app data).
-keepattributes *Annotation*, InnerClasses
# JNI resolves tensor/session classes and members by their original names.
-keep class ai.onnxruntime.** { *; }
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.kaynzhang.doudizhu.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.kaynzhang.doudizhu.**$$serializer { *; }
-keepclassmembers class com.kaynzhang.doudizhu.** {
    *** Companion;
}

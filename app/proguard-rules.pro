# kotlinx.serialization: keep generated serializers of our @Serializable classes (saved games and app data).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.kaynzhang.doudizhu.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.kaynzhang.doudizhu.**$$serializer { *; }
-keepclassmembers class com.kaynzhang.doudizhu.** {
    *** Companion;
}

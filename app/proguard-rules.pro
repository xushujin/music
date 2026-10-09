# ===== kotlinx.serialization =====
# 序列化器是编译期生成的，但 R8 需要保留 @Serializable 类的合成 serializer 入口
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.azhan.music.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.azhan.music.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.azhan.music.data.**$$serializer { *; }

# ===== 说明 =====
# media3 / Compose / DataStore 的 AAR 都自带 consumer proguard 规则，
# 这里不需要再手写 -keep，写了反而会阻止裁剪、把包撑大。
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.**

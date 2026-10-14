-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.wuhuan.edgepro.** {
    *** Companion;
}
-keepclasseswithmembers class com.wuhuan.edgepro.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wuhuan.edgepro.**$$serializer { *; }

# ProGuard rules for Kereta Malam Cafe POS
-keepattributes *Annotation*,InnerClasses,Signature
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclasseswithmembers class id.keretamalam.cafe.data.model.** { *; }

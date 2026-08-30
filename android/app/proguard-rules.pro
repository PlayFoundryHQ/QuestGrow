# QuestGrow Android — R8 / ProGuard keep rules for the release (minified) build.
# Without these, R8 strips reflection-driven code (kotlinx.serialization
# serializers, Retrofit interface methods) and the release build fails at
# runtime. Keep them minimal and specific.

# ---- kotlinx.serialization ------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# keep every @Serializable class + its generated $$serializer
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers @kotlinx.serialization.Serializable class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class hq.playfoundry.questgrow.**$$serializer { *; }
-keepclassmembers class hq.playfoundry.questgrow.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# our wire DTOs are only touched via reflection by the serializer
-keep class hq.playfoundry.questgrow.data.net.** { *; }

# ---- Retrofit / OkHttp --------------------------------------------------
-keep,allowobfuscation,allowshrinking interface hq.playfoundry.questgrow.data.net.QuestGrowApi
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes Signature, Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Retrofit 2.11 ships its own R8 rules; these cover the interop edges.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ---- Compose / app ---------------------------------------------------
-keep class hq.playfoundry.questgrow.QuestGrowApp { *; }
-keepclassmembers class ** extends androidx.lifecycle.ViewModel { <init>(...); }

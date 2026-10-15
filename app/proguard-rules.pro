# ───────────────────────── mihomo (Go Mobile) ─────────────────────────
-keep class go.** { *; }
-keep class mihomocore.** { *; }

# Our mihomocore.Platform implementation is invoked from native Go via JNI; its method
# names/signatures must survive. The vpn package is small, so keep it wholesale.
-keep class io.github.yulbax.erkin.vpn.** { *; }

# ───────────────────────── byedpi (JNI, name-based lookup) ─────────────────────────
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class io.github.yulbax.erkin.engine.ByeDpi { *; }

# ───────────────────────── kotlinx.serialization ─────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class io.github.yulbax.erkin.**$$serializer { *; }
-keepclassmembers class io.github.yulbax.erkin.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# ───────────────────────── Room ─────────────────────────
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class io.github.yulbax.erkin.** { *; }
-dontwarn androidx.room.paging.**

# ───────────────────────── misc ─────────────────────────
-dontwarn kotlinx.coroutines.**

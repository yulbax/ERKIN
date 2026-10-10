-keep class io.github.yulbax.erkin.desktop.MainKt { public static void main(java.lang.String[]); }

-keep class com.sun.jna.** { *; }
-keep interface * extends com.sun.jna.Library { *; }
-dontwarn com.sun.jna.**

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class androidx.sqlite.driver.bundled.** { *; }

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class io.github.yulbax.erkin.**$$serializer { *; }
-keepclassmembers class io.github.yulbax.erkin.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class **_Impl { *; }
-keep @androidx.room.Entity class io.github.yulbax.erkin.** { *; }

-keep class kotlinx.coroutines.swing.SwingDispatcherFactory { *; }
-keep class * implements kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class * implements kotlinx.coroutines.CoroutineExceptionHandler { *; }

-dontwarn kotlinx.coroutines.**
-dontwarn androidx.room.paging.**
-dontwarn org.slf4j.**
-dontnote **

-keep class com.kdroid.composetray.** { *; }
-dontwarn com.kdroid.**
-dontwarn io.github.kdroidfilter.**

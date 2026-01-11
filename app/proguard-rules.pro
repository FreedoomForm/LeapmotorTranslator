# Proguard rules for Leapmotor Translator

# Keep ML Kit translation classes
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Accessibility Service
-keep class com.leapmotor.translator.TranslationService { *; }

# Keep OpenGL shader-related classes
-keep class com.leapmotor.translator.renderer.** { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

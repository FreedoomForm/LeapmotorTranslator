# ============================================================================
# LeapmotorTranslator ProGuard Rules
# Optimized for release builds with Hilt, Room, and ML Kit
# ============================================================================

# ============================================================================
# GENERAL ANDROID RULES
# ============================================================================

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================================
# KOTLIN
# ============================================================================

-keep class kotlin.Metadata { *; }

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep data classes
-keepclassmembers class * {
    public <init>(...);
}

# ============================================================================
# HILT / DAGGER
# ============================================================================

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManager { *; }

# Keep @Inject annotated constructors
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}

# Keep @HiltViewModel annotated classes
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep Hilt entry points
-keep @dagger.hilt.android.EarlyEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }

# Don't warn about Hilt internals
-dontwarn dagger.hilt.internal.**
-dontwarn dagger.internal.**

# ============================================================================
# ROOM DATABASE
# ============================================================================

# Keep Room entities
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }

# Keep entity fields
-keepclassmembers @androidx.room.Entity class * {
    <fields>;
}

# Keep DAO methods
-keepclassmembers @androidx.room.Dao interface * {
    <methods>;
}

# Room generated implementations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class **_Impl { *; }

# ============================================================================
# VIEWMODEL
# ============================================================================

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep ViewModel factory
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory { *; }

# ============================================================================
# GOOGLE ML KIT
# ============================================================================

-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_translate.** { *; }

-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ============================================================================
# APP SPECIFIC
# ============================================================================

# Keep Result sealed class
-keep class com.leapmotor.translator.core.Result { *; }
-keep class com.leapmotor.translator.core.Result$* { *; }

# Keep UiState sealed interface
-keep class com.leapmotor.translator.core.UiState { *; }
-keep class com.leapmotor.translator.core.UiState$* { *; }

# Keep domain models
-keep class com.leapmotor.translator.domain.model.** { *; }

# Keep accessibility service
-keep class com.leapmotor.translator.TranslationService {
    <init>();
    void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent);
    void onInterrupt();
}

# Keep renderer classes
-keep class com.leapmotor.translator.renderer.OverlayRenderer { *; }
-keep class com.leapmotor.translator.renderer.EraserSurfaceView { *; }
-keep class com.leapmotor.translator.renderer.TextOverlay { *; }
-keep class com.leapmotor.translator.renderer.TextOverlay$TranslatedText { *; }

# Keep entity classes for Room
-keep class com.leapmotor.translator.data.local.entity.** { *; }

# ============================================================================
# COROUTINES
# ============================================================================

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ============================================================================
# LOGGING (Remove debug logs in release)
# ============================================================================

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep error logging
-keep class com.leapmotor.translator.core.Logger {
    public static void e(...);
    public static void w(...);
    public static void wtf(...);
}

# ============================================================================
# OPTIMIZATION
# ============================================================================

-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# ============================================================================
# DEBUG (Keep for stack traces)
# ============================================================================

-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes *Annotation*

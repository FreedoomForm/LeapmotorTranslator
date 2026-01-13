# Consumer ProGuard rules for core:data module

# Keep Room entities
-keep class com.leapmotor.translator.core.data.local.entity.** { *; }

# Keep Room DAOs
-keep interface com.leapmotor.translator.core.data.local.dao.** { *; }

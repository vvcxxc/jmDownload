# Keep the ImageProcessor SPI (ServiceLoader) so descrambling keeps working.
-keep class io.github.jukomu.jmcomic.android.support.AndroidImageProcessor { *; }
-keep interface io.github.jukomu.jmcomic.core.image.spi.ImageProcessor { *; }
-keepnames class * implements io.github.jukomu.jmcomic.core.image.spi.ImageProcessor

# The jmcomic library uses Gson/reflection for its API models. Keep the
# library intact; it is small compared with the rest of the dependency graph.
-keep class io.github.jukomu.** { *; }
-keep interface io.github.jukomu.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep ServiceLoader metadata
-keepattributes *Annotation*
-dontwarn org.slf4j.**
-dontwarn org.apache.**
-dontwarn javax.annotation.**
# Desktop-only image processor in jmcomic-core references AWT/ImageIO, which
# do not exist on Android. It is never selected on-device.
-dontwarn java.awt.**
-dontwarn javax.imageio.**

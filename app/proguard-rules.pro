# Debug builds ship un-minified. If release minification is ever enabled,
# keep the ImageProcessor SPI (ServiceLoader) so descrambling keeps working.
-keep class io.github.jukomu.jmcomic.android.support.AndroidImageProcessor { *; }
-keep interface io.github.jukomu.jmcomic.core.image.spi.ImageProcessor { *; }
-keepnames class * implements io.github.jukomu.jmcomic.core.image.spi.ImageProcessor
# Keep ServiceLoader metadata
-keepattributes *Annotation*
# jmcomic library uses reflection-free ServiceLoader; nothing else special.

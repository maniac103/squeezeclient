# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-dontobfuscate

# see https://github.com/androidx/constraintlayout/issues/428
-keepclassmembers class * extends androidx.constraintlayout.motion.widget.Key {
  public <init>();
}

-keep class de.maniac103.squeezeclient.ui.widget.RoundedCornerProgressDrawable

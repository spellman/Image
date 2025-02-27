# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/cort/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include absolutePath and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Crashlytics
#   as per https://docs.fabric.io/android/crashlytics/dex-and-proguard.html
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Picasso
#   as per https://github.com/square/picasso
-dontwarn com.squareup.okhttp.**

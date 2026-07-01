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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class org.openardf.radiooracle.backend.room.entity.** { *; }
-keep class org.openardf.radiooracle.backend.room.enums.** { *; }
-keep class org.openardf.radiooracle.backend.files.json.** { *; }
-keep class org.openardf.radiooracle.ui.races.** {*;}

# Xerces references optional Java XML APIs that are not present on Android.
# The app does not use those optional StAX/DOM traversal paths at runtime.
-dontwarn javax.xml.stream.**
-dontwarn javax.xml.transform.stax.**
-dontwarn org.w3c.dom.ElementTraversal
-dontwarn org.w3c.dom.events.**
-dontwarn org.w3c.dom.ls.LSSerializerFilter
-dontwarn org.w3c.dom.ranges.**
-dontwarn org.w3c.dom.traversal.**

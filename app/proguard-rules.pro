# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in E:\Android\SDK/tools/proguard/proguard-android.txt
# You can edit the include dir and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class username to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;

-dontwarn org.apache.commons.net.**
-keep class org.apache.commons.net.** { *;}
-keep interface org.apache.commons.net.** { *;}

-dontwarn org.apache.ftpserver.**
-keep class org.apache.ftpserver.** { *;}
-keep interface org.apache.ftpserver.** { *;}

-dontwarn org.apache.mina.**
-keep class org.apache.mina.** { *;}
-keep interface org.apache.mina.** { *;}

-dontwarn org.slf4j.**
-keep class org.slf4j.** { *;}
-keep interface org.slf4j.** { *;}

-keep class cn.gembit.transdev.R$raw
-keepclassmembers class cn.gembit.transdev.R$raw {public static <fields>;}
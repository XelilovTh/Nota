# NewPipeExtractor
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Mozilla Rhino JS engine (used by NewPipeExtractor)
-keep class org.mozilla.javascript.* { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**

# Scripting & dynamic invocation
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**

# Networking warnings
-dontwarn okhttp3.**
-dontwarn okio.**

# Retain the JNI-facing names for the animated WebP decoder.
# Do not keep all Compose or Material icon classes: R8 should remove
# the many unused icons from material-icons-extended.
-keep class com.aureusapps.android.webpandroid.** { *; }
-keep class com.bumptech.glide.gifdecoder.** { *; }
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature

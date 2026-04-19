# Preserve line numbers in crash reports
-keepattributes SourceFile,LineNumberTable

# --- JGit ---
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**

# --- Apache MINA sshd (JGit SSH transport) ---
-keep class org.apache.sshd.** { *; }
-dontwarn org.apache.sshd.**

# --- BouncyCastle / PGPainless ---
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.pgpainless.** { *; }
-dontwarn org.pgpainless.**

# --- Hilt / Dagger generated code ---
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }
-dontwarn dagger.hilt.**

# --- Kotlin serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# --- Apache Commons Text (Levenshtein) ---
-keep class org.apache.commons.text.** { *; }
-dontwarn org.apache.commons.text.**

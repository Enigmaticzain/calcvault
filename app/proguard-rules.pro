# ============================================================
# CalcVault ProGuard Rules
# Aggressive obfuscation — makes reverse engineering very hard
# ============================================================

# ─── Keep application entry points ──────────────────────────
-keep class com.calcvault.ui.calculator.CalculatorActivity { *; }
-keep class com.calcvault.ui.unlock.PassphraseActivity     { *; }
-keep class com.calcvault.ui.main.MainVaultActivity        { *; }
-keep class com.calcvault.ui.main.DecoyActivity            { *; }
-keep class com.calcvault.storage.UsbBroadcastReceiver     { *; }

# ─── Obfuscate everything else aggressively ─────────────────
-repackageclasses 'a'
-allowaccessmodification
-overloadaggressively
-flattenpackagehierarchy

# ─── Remove all logging (strip Log.d, Log.e, etc.) ──────────
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ─── Strip SourceFile and line numbers from stack traces ────
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable

# ─── SQLCipher ───────────────────────────────────────────────
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# ─── AndroidX Security ───────────────────────────────────────
-keep class androidx.security.crypto.** { *; }

# ─── Biometric ───────────────────────────────────────────────
-keep class androidx.biometric.** { *; }

# ─── Kotlin metadata (needed for reflection-safe code) ───────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# ─── WebRTC ──────────────────────────────────────────────────
-keep class org.webrtc.** { *; }
-keep class org.webrtc.Environment { *; }
-keep class org.webrtc.Environment$Builder { *; }
-keep class org.webrtc.PeerConnectionFactory$Builder { *; }
-dontwarn org.webrtc.**

# ─── Lottie ──────────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }

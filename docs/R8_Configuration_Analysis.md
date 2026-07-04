# R8 Configuration Analysis

## Configuration

| Setting                      | Status                                                    |
| ---------------------------- | --------------------------------------------------------- |
| AGP version                  | 9.2.1 ✓                                                   |
| `isMinifyEnabled`            | true ✓                                                    |
| `isShrinkResources`          | true ✓                                                    |
| Default proguard file        | `proguard-android-optimize.txt` ✓                         |
| R8 Full Mode                 | Enabled by default (AGP 8.0+), no override disabling it ✓ |
| Optimized resource shrinking | Built-in at AGP 9.0+ ✓                                    |

Configuration is correct and fully optimized for AGP 9.

---

## Keep Rule Analysis

Rules ordered by impact (broadest first).

---

### 1. `-keep class org.eclipse.jgit.** { *; }`

**Action: Refine**

Keeps every class and member in JGit including unused transports and protocols. JGit uses `ServiceLoader` and `Class.forName` internally to load transport implementations, object formats, and diff algorithms — so classes referenced only via service discovery would be removed by R8. The full package sweep is overly broad.

Identify which JGit service interfaces have implementations loaded by name at runtime, then write targeted rules:

```proguard
# Keep JGit's ServiceLoader-discovered implementations
-keep class * implements org.eclipse.jgit.transport.Transport { <init>(...); }
-keep class * implements org.eclipse.jgit.lib.ObjectDatabase { <init>(...); }
```

For classes used directly in code (`Git`, `SshdSessionFactory`, `CanonicalTreeParser`, `DiffEntry`, etc.), R8 traces these through the call graph — no keep rule needed for those.

---

### 2. `-keep class org.apache.sshd.** { *; }`

**Action: Refine**

Keeps all of Apache MINA sshd including unused SSH cipher/kex/mac/compression factory implementations. MINA sshd loads factories via `IoServiceFactoryFactory` and service registries — those need protection. Direct-use classes (`SshdSessionFactory`, `KeyUtils`, `BuiltinDigests`) are traced by R8 automatically.

Use targeted rules for the factory interfaces loaded dynamically:

```proguard
-keep class * implements org.apache.sshd.common.Factory { <init>(); }
-keep class * implements org.apache.sshd.common.NamedFactory { <init>(); }
-keep class * implements org.apache.sshd.common.io.IoServiceFactoryFactory { <init>(); }
```

---

### 3. `-keep class org.bouncycastle.** { *; }`

**Action: Refine**

Covers all of BouncyCastle including unrelated algorithm families (elliptic curves, symmetric ciphers, ASN.1 parsers). The JCE provider mechanism (`Security.insertProviderAt(BouncyCastleProvider(), 1)`) loads algorithm implementations by name string at runtime, so those must be kept. However the rule is broader than what is needed.

Focus keeps on the JCE SPI implementations and the packages actively used:

```proguard
# JCE algorithm implementations loaded by name via BouncyCastleProvider
-keep class org.bouncycastle.jcajce.provider.** { <init>(...); }
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }

# Used directly in code
-keep class org.bouncycastle.jce.** { *; }
-keep class org.bouncycastle.openpgp.** { *; }
-keep class org.bouncycastle.asn1.x509.SubjectPublicKeyInfo { *; }
-keep class org.bouncycastle.crypto.** { *; }
```

---

### 4. `-keep class org.pgpainless.** { *; }`

**Action: Refine**

PGPainless is used only through its public API (`PGPainless.getInstance()`, `ConsumerOptions`, `PGPainless.asciiArmor()`). These are direct calls — R8 can trace them. The internal implementation packages (`org.pgpainless.implementation`, `org.pgpainless.util`) are not directly referenced in app code and do not need blanket protection.

Investigate whether PGPainless loads any of its own components via `Class.forName` or `ServiceLoader`. If not, R8 should handle this without any keep rule. If it does use service discovery internally, narrow to:

```proguard
-keep class org.pgpainless.PGPainless { *; }
-keep class org.pgpainless.decryption_verification.** { *; }
-keep class org.pgpainless.key.** { *; }
```

---

### 5. `-keep class org.apache.commons.text.** { *; }`

**Action: Remove**

Only `LevenshteinDistance.getDefaultInstance()` is used. This is a static factory call with no reflection — R8 traces it through the call graph and keeps it automatically. No manual rule needed.

Remove `-keep class org.apache.commons.text.** { *; }` and `-dontwarn org.apache.commons.text.**`.

---

### 6. `-keep class javax.management.** { *; }`

**Action: Investigate — likely replace with `-dontwarn`**

Comment says these are stubs for Android (which lacks JMX). If these stubs are in a library JAR and only referenced in code paths that never execute on Android, R8 would tree-shake them anyway. The keep rule prevents that.

Check whether removing this rule triggers `ClassNotFoundException` at runtime on Android. If the classes are only referenced by dead code paths in JGit/MINA, they don't need to exist. Replace with:

```proguard
-dontwarn javax.management.**
```

---

### 7. `-keep class dagger.hilt.** { *; }`

**Action: Remove**

Hilt bundles comprehensive consumer ProGuard rules in its AAR. This rule keeps the entire Hilt library — its internal managers, generated code infrastructure, and lifecycle integrations — preventing R8 from shrinking unused Hilt internals. The bundled rules already handle what is needed.

Remove this rule. Verify the app still builds and runs correctly after removal.

---

### 8. `-keep class kotlinx.serialization.** { *; }`

**Action: Remove**

`kotlinx.serialization` is an official Kotlinx library that bundles its own consumer ProGuard rules. Per those rules, the serializer infrastructure (`$$serializer` classes, `Companion` objects with `serializer()`) is preserved automatically. This manual rule prevents R8 from shrinking unused serializer implementations and internal utilities.

Remove this rule.

---

### 9. `-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }`

**Action: Investigate — likely covered by Hilt plugin**

The Hilt Gradle plugin generates ProGuard rules during compilation for generated component classes (including `ActivityComponentManager` subclasses). Check the build output at `app/build/generated/source/kapt/.../hilt_aggregated_deps/` or the generated ProGuard rules in the intermediate build artifacts for equivalent coverage.

If the Hilt plugin already generates a rule for these classes, remove this manual rule. If not, keep it but narrow the member spec:

```proguard
-keepclassmembers class * extends dagger.hilt.android.internal.managers.ActivityComponentManager {
    <init>(...);
}
```

---

### 10. `-keep @kotlinx.serialization.Serializable class * { *; }`

**Action: Refine**

The `@Serializable` annotation is used on Navigation 3 route keys (`Splash`, `Welcome`, `EntryDetail`, etc.). These classes need their class names preserved (for back-stack serialization) and their fields preserved (for parameter encoding). The `{ *; }` member spec keeps all methods too — unnecessary since R8 traces method calls.

Replace with:

```proguard
-keepclassmembers @kotlinx.serialization.Serializable class * { <fields>; }
-keep @kotlinx.serialization.Serializable class * { <init>(...); }
```

---

## Testing

After applying changes, run UI Automator tests covering:

- Navigation flows (back stack restore after process death)
- Git clone/pull operations
- GPG key import and decryption
- SSH key generation and authentication
- Autofill and credential provider flows

# Fork edits

This tree is a Tencent fork of Kotlin. Upstream files stay as close to upstream as possible. These rules apply to every edit on the fork, in addition to [guidelines.md](guidelines.md).

## Mark the delta

A change inside an upstream file is wrapped in a region. The region contains only the lines that differ from upstream.

```kotlin
// region @Tencent: Allow libbacktrace source info on OHOS.
|| this.family == Family.OHOS
// endregion
```

Use the host language's comment marker. The label is always `region @Tencent: <one sentence>`.

| Language | Open | Close |
| --- | --- | --- |
| Kotlin, Java, C, C++ | `// region @Tencent: ...` | `// endregion` |
| Properties, Gradle, shell, Python | `# region @Tencent: ...` | `# endregion` |

- The sentence states the behavior this block adds. It does not say "Tencent code".
- `endregion` is on its own line and has no description.
- The token is `region`, not `@region`. The `@` belongs to `@Tencent`, so the IDE can fold the block.
- Two unrelated edits in one function are two regions.
- A file that exists only on this fork, such as `Ohos.kt`, has no region. The license header already marks its origin.
- Do not reformat or reorder surrounding upstream lines to make the region look tidy.

## Dependencies

`internalOnlyDependencies` in `kotlin-native/konan/konan.properties` is the list of packages that exist only on the intranet. An external distribution (`distributionInternal=false`) must not request them. An internal distribution is built with `-Pkuikly.internal=true` and downloads those packages from `internalDependenciesUrl`.

Other native dependencies use `dependenciesUrl` first and `https://download.jetbrains.com/kotlin/native` when the mirror returns an HTTP error.

Adding an intranet package means two property edits: put the name in the relevant `dependencies.*` list, and add it to `internalOnlyDependencies`. Do not hard-code the package name in Kotlin.

## What not to change

- Do not patch LLVM for OHOS. The driver passes sysroot, `-I`, and `-L` itself.
- Do not publish a repacked OHOS sysroot to the JetBrains dependency repository.
- Do not put `scripts/kuikly-base/internal/` under version control.

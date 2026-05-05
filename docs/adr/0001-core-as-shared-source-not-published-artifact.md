# 0001 — core as shared source, not published artifact

| | |
|---|---|
| Status | accepted |
| Date | 2026-05-04 |
| Spec | [2026-05-monorepo-and-gradle](../specs/2026-05-monorepo-and-gradle.md) |

## Decision

`JvmFlagsLayerPlan` 與其 test 住在 `core/src/{main,test}/java/...` 共享 source dir，**不**以獨立 Maven Central artifact 形式發布。`core/` 沒有 `pom.xml`、沒有 `build.gradle`、不是 module。Maven 端透過 `build-helper-maven-plugin` 的 `add-source` / `add-test-source` 接入，Gradle 端透過 `sourceSets.main.java.srcDirs += '../core/src/main/java'` 接入。兩個 jar 各自編譯 core source，產出 byte-identical 的 core classes。

Maven Central 上發布的 artifact 為 **2 個**：`jib-jvm-flags-extension-maven` 與 `jib-jvm-flags-extension-gradle`。

## Considered Options

- **A. core 獨立發到 Maven Central**（issue 原規劃，3 artifact）
- **B-I. core 是 Maven module 但不 deploy；maven-shade-plugin / Gradle shadow plugin 把 core class shade 進 maven 與 gradle 的 jar**
- **B-II. core 不是 module，僅 shared source；兩端各自編譯**（**選用**）
- **B-III. core/ 不存在；source 留在 maven module，gradle 跨界讀 maven 的 source dir**

## Why B-II（拒絕其他三項）

**拒絕 A**：spec 沒有第三方公開 `JvmFlagsLayerPlan` 重用的需求陳述；issue Not Doing 明確排除「為 core 抽介面 + 實作分層」，暗示 core 是內部實作。發 core 製造一個不必要的「跨 module 版本相容矩陣」問題、多一份 release-please PR、多一份 GPG 簽章流程，對「沒有 consumer」的 artifact 完全不成比例。一旦發了，未來想拿掉是 breaking。

**拒絕 B-I**：仍然需要 core 有版號、install 到 mavenLocal、CI 順序耦合（gradle 必須等 maven install 後才能跑）、release-please 仍要追第三個 file。「半發布」模式把 A 的複雜度幾乎全留，沒拿到 A 的好處（因為沒 publish 給外部）。

**拒絕 B-III**：maven module 變「core 的 owner」，gradle 寄生 — 結構不對稱會在「新增 class 放哪、test 算誰的、`@since` 標誌怎麼寫」等問題上製造長期 review 摩擦。

**選 B-II**：把「shading 工程」化約為「compilation」。沒有 maven-shade-plugin，沒有 Gradle shadow plugin，沒有 dependency-reduced-pom 的雙重設定，沒有 install ordering，沒有額外版號要管。代價只有 IDE / 結構直覺問題（`core/` 沒 pom 看起來怪），但 jib-extensions 官方第一方 extensions 用類似手法，先例足。

## Consequences

- Maven Central 上**沒有** `jib-jvm-flags-extension-core` artifact 可資依賴。任何想重用 `JvmFlagsLayerPlan` 的第三方需要 vendor 或 fork（與此設計初衷一致 — 它不是 public API）。
- `JvmFlagsLayerPlan` 的 package 為 `tw.com.softleader.cloud.tools.jib.core`（自 1.0.5 的 `jib.maven` 搬遷 — 對 outlier 直接 import 該 helper 的使用者構成 v1.x 中的 source-incompat 微調，release notes 公告）。
- 兩個 jar 內各自包含一份 core classes（byte-identical）。同時把 maven 與 gradle 兩個 artifact 都放進同一個 application classpath 是錯誤用法但不會發生（jib-maven-plugin 與 jib-gradle-plugin 不會在同一 application 共存）。
- maven `mvn dependency:tree` 看不到 core 的 dependency line；core 是 source contribution 而非 dependency。
- Spotless / license-header 在兩端必須**各自**對 `../core/src/main/java/**/*.java` 範圍生效；`google-java-format` 版本必須 pin 到 properties 雙端共用，避免 byte 差異。Gradle 端 Spotless 設為 check-only，避免兩端 apply 互打。
- 反轉此決定（將 core 升格為發布 artifact）需要新 ADR + groupId/artifactId 規劃 + 完整版號政策；屬「Ask first」邊界。

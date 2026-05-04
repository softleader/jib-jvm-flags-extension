# Spec: Monorepo Restructure + Gradle Module

| | |
|---|---|
| Status | Draft, awaiting review |
| Issue | [#69](https://github.com/softleader/jib-jvm-flags-extension-maven/issues/69) |
| Author | Shihyu Ho |
| Spike | `~/code/spike-jib-gradle-extension/FINDINGS.md` (local, not committed) |
| ADR | [0001 — core as shared source, not published artifact](../adr/0001-core-as-shared-source-not-published-artifact.md) |
| Date | 2026-05-04 |

---

## 1. Objective

把目前 Maven-only 的 Jib JVM Flags extension（`tw.com.softleader.cloud.tools:jib-jvm-flags-extension-maven`）擴展為同時提供 Maven 與 Gradle 雙版本的 single-source-of-truth monorepo，產出 **2 個** Maven Central artifact：

- `jib-jvm-flags-extension-maven`（既有，groupId / artifactId / 主類 FQN 不變）
- `jib-jvm-flags-extension-gradle` (新)

> 共用程式碼（`JvmFlagsLayerPlan`）住在 `core/` 共享 source dir，**不**獨立發 Maven Central（見 [ADR-0001](../adr/0001-core-as-shared-source-not-published-artifact.md)）。兩個 artifact 各自編 core source，產出 byte-identical 的 core class。

驅動原因：對應 [softleader/kapok#731](https://github.com/softleader/kapok/issues/731) 公司導入 Gradle 評估的工作清單，先把基礎設施鋪好，避免評估拍板後才現做。

**Success（高層）**：

1. 既有 Maven 使用者升級**完全無感** — 範圍精準化為「使用 `<implementation>` 設定的使用者完全無感；極少數直接 `import` helper class（`JvmFlagsLayerPlan`）的 outlier，視為 v1.x 中可接受的微調，由 release notes 公告 package `jib.maven` → `jib.core` 搬遷」
2. 新 Gradle 使用者可在 jib-gradle-plugin 配置中啟用 `JvmFlagsExtension`，行為與 Maven 端等價
3. 兩個 artifact **同版齊發**（unified versioning，非各自獨立）；release 流程從 v1（Jenkinsfile-bump-version + release.yml）切到 release-workflow v2（release-please）

## 2. Tech Stack

| 元件 | 版本 / 設定 | 來源 |
|---|---|---|
| Java baseline | 11 | 對齊既有 repo；Gradle 8.x 也支援 |
| Maven | ≥3.9 | 對齊既有 repo |
| Gradle | 8.x | 給 `gradle/` module 用；CI/local 用 wrapper |
| `jib-maven-plugin-extension-api` | 0.4.0 | 既有 |
| `jib-gradle-plugin-extension-api` | 0.4.0 | 同 jib release line |
| `jib-gradle-plugin` | 3.4.0（declared compileOnly） | 從 Gradle Plugin Portal（不是 Maven Central）；README 聲明相容 3.4.x / 3.5.x |
| `build-helper-maven-plugin` | latest stable | maven 端接入 `../core/src/main/java` 與 `../core/src/test/java` |
| `google-java-format` | pin 同一版本到 properties，maven 與 gradle 雙端對齊 | 避免兩端 spotless 在 core/ 上產生 byte 差異 |
| Lombok | 1.18.42+ | 既有 |
| Commons-lang3 | 既有版本 | 既有 |
| JUnit Jupiter / AssertJ / Mockito | 既有版本 | 既有 |
| GPG / Maven Central | central-publishing-maven-plugin（Maven 側）；Gradle `maven-publish` + 簽章插件（Gradle 側） | 兩條 publish 路徑共存於一個 repo |
| release-please | release-workflow v2 skill 規格；**unified versioning** | 取代 v1 |

## 3. Commands

```bash
# Maven 側（含 core/ 共享 source 接入後的 maven module）— 從 repo root 執行
mvn -B clean verify                    # build + test（core 的 test 也在這裡跑）
mvn -B clean install                   # local install
mvn -B clean deploy -P release         # 推 Maven Central（CI 觸發）

# Gradle 側（gradle module）— cd 進去
cd gradle
./gradlew build                        # build + test（core source 一起編進 gradle jar）
./gradlew publish -Prelease            # 推 Maven Central（CI 觸發；profile 名待 release-workflow 確定）

# 整合（CI 跑單 job 串接）
mvn -B clean verify && (cd gradle && ./gradlew build)
```

> **註 1**：parent `pom.xml` 的 `<modules>` **只列 `maven`**。`core/` 不是 Maven module（沒有 pom.xml），由 maven module 透過 `build-helper-maven-plugin` 接入。`gradle/` 不在 Maven aggregator 內（決議 5.2A，見 §5）。
>
> **註 2**：CI 不需要 `mvn install`，因為 gradle 端不消費 mavenLocal — 兩端各自編譯 `../core/src/main/java`，產出 byte-identical bytecode。

## 4. Project Structure

```
/jib-jvm-flags-extension/                            （repo 已 rename）
├── pom.xml                                          parent aggregator（含 maven only；不含 core）
├── core/                                            ← 共享 source dir，**不是** Maven module
│   └── src/
│       ├── main/java/tw/com/softleader/cloud/tools/jib/core/
│       │   └── JvmFlagsLayerPlan.java               （從 1.0.5 的 jib.maven 搬到 jib.core）
│       └── test/java/tw/com/softleader/cloud/tools/jib/core/
│           └── JvmFlagsLayerPlanTest.java           （由 maven 端的 surefire 透過 add-test-source 執行）
├── maven/
│   ├── pom.xml                                      （build-helper 接 ../core/src/main/java + test）
│   └── src/
│       ├── main/java/tw/com/softleader/cloud/tools/jib/maven/
│       │   ├── JvmFlagsExtension.java               （unchanged FQN — 對使用者就是 1.0.5 那個）
│       │   └── PluginConfigLocation.java            （Maven-only utility）
│       ├── main/resources/META-INF/services/
│       │   └── com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension
│       └── test/java/tw/com/softleader/cloud/tools/jib/maven/
│           ├── JvmFlagsExtensionTest.java
│           └── PluginConfigLocationTest.java
├── gradle/                                          ← 自有 Gradle build，獨立於 Maven aggregator
│   ├── build.gradle                                 （sourceSets 加 ../core/src/main/java；不加 test）
│   ├── settings.gradle
│   ├── gradle.properties                            （version=…，release-please extra-files 同步點）
│   ├── gradlew / gradlew.bat / gradle/wrapper/...
│   └── src/
│       ├── main/java/tw/com/softleader/cloud/tools/jib/gradle/
│       │   └── JvmFlagsExtension.java
│       ├── main/resources/META-INF/services/
│       │   └── com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension
│       └── test/java/tw/com/softleader/cloud/tools/jib/gradle/
│           └── JvmFlagsExtensionTest.java
├── docs/
│   ├── specs/2026-05-monorepo-and-gradle.md         （本文件）
│   └── adr/
│       └── 0001-core-as-shared-source-not-published-artifact.md
├── .github/workflows/                               （v1 release pipeline 移除，v2 新增）
├── Jenkinsfile                                      （**保留**，加 gradle build step）
├── README.md                                        （新增 Gradle 使用段落）
└── （其他既有檔：CONTRIBUTING.md, LICENSE, copyright-license-header-template.txt, ...）
```

**移除**：`Jenkinsfile-bump-version`、`Jenkinsfile-release`、`.github/workflows/bump-version.yml`、`.github/workflows/release.yml`。

**保留並修改**：`Jenkinsfile`（一般 build trigger，GitHub push webhook 觸發），加上 `(cd gradle && ./gradlew build)` step。

## 5. Build Strategy（核心架構決策）

### 5.1 jib-extensions 雙生 build tool 結構

**決議 5.2A：Maven aggregator drives maven；gradle/ uses Gradle build。**

**理由：**
- `org.gradle.api.Project`（gradle/JvmFlagsExtension 需要）來自 Gradle 的 `gradle-api`，Gradle 設計上不發到 Maven Central
- `com.google.cloud.tools:jib-gradle-plugin` 也只在 Gradle Plugin Portal 不在 Maven Central
- Spike 試過 system-scoped 指本機 SDKMAN gradle 8.5 的 jar 可以編，但 CI / 跨機器易壞
- 對齊 jib-extensions 官方第一方 extension 的做法（每個 -gradle module 都用 Gradle build）
- Issue 的精神「不引入 Gradle 為整 repo 的 build tool」仍成立 — maven 端仍由 Maven 驅動

**代價（接受）：**
- CI 跑兩段（`mvn` + `./gradlew`），不能單一指令一鍵
- 開發者需具備兩個 build tool 的基本知識（但 Gradle 透過 wrapper，不需要本機裝）
- release-please 用 unified versioning + `extra-files` 同步 `maven/pom.xml` `<version>` 與 `gradle/gradle.properties` `version`
- Spotless / license header 設定要在兩端各裝一次（formatter 版本對 properties pin 一致）

**不做的事（明確排除）：**
- 不 commit `gradle-core-api-X.X.jar` 進 repo（`<scope>system</scope>` 方案 5.2B，否決）
- 不嘗試自架 Maven mirror（5.2C，否決）

### 5.2 core/ 共享 source dir 機制（B-II）

`core/` **不是** Maven 也不是 Gradle module，只是兩端共用的 source 資料夾，不對外發 artifact（見 [ADR-0001](../adr/0001-core-as-shared-source-not-published-artifact.md)）。

機制：

- 共享源碼：`core/src/main/java/tw/com/softleader/cloud/tools/jib/core/JvmFlagsLayerPlan.java`，package 為 `jib.core`（從 1.0.5 的 `jib.maven` 搬遷）
- 共享測試：`core/src/test/java/tw/com/softleader/cloud/tools/jib/core/JvmFlagsLayerPlanTest.java`
- **maven 端接入**：`maven/pom.xml` 用 `build-helper-maven-plugin` 的 `add-source` 加 `../core/src/main/java`、`add-test-source` 加 `../core/src/test/java`
- **gradle 端接入**：`gradle/build.gradle` 用 `sourceSets.main.java.srcDirs += '../core/src/main/java'`（**不接** test — core test 只 maven 端跑）
- 兩個 jar 各自編譯 core source，產出 byte-identical 的 core classes；不需要 maven-shade / gradle shadow plugin

**代價：**
- maven `mvn dependency:tree` 看不到 core 是 dep（沒這個 dep，純 source 接入），文件需要解釋
- IDE 偶爾要手動 mark `core/src/main/java` 為 source root；建議 commit `.idea/` 設定協助
- 任何 `core/*.java` 改動同時影響兩個 artifact，unified versioning 自然處理 bump

## 6. Code Style

延用既有 repo 已配置的：

- **Spotless** 跑 `googleJavaFormat`，version 在 properties 中 pin 同值，maven 與 gradle 雙端共用
  - Maven 端：`spotless-maven-plugin`，apply 模式（既有），`<includes>` 顯式列 `../core/src/main/java/**/*.java`
  - Gradle 端：`com.diffplug.spotless` Gradle plugin，**check 模式**（避免兩端 apply 在同一檔案上打架），`target` 顯式列 `../core/src/main/java/**/*.java`
- **License header**：依 `copyright-license-header-template.txt`，**改用 Spotless 內建的 `licenseHeader` step**（不再用 `license-maven-plugin`，也不引入 `com.github.hierynomus.license`），兩端各裝
- **Encoding**：UTF-8
- **POM 排序**：sortPom（Maven 側現有設定）

實際 Gradle 端範例（最終樣貌會跟既有 maven module 等價）：

```java
// gradle/src/main/java/tw/com/softleader/cloud/tools/jib/gradle/JvmFlagsExtension.java
package tw.com.softleader.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.gradle.ContainerParameters;
import com.google.cloud.tools.jib.gradle.JibExtension;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import org.gradle.api.Project;
import tw.com.softleader.cloud.tools.jib.core.JvmFlagsLayerPlan;

public class JvmFlagsExtension implements JibGradlePluginExtension<Void> {

  public static final String PROPERTY_SKIP_IF_EMPTY = "skipIfEmpty";
  public static final String PROPERTY_SEPARATOR = "separator";
  public static final String PROPERTY_FILENAME = "filename";
  public static final String PROPERTY_MODE = "mode";

  @Override
  public Optional<Class<Void>> getExtraConfigType() {
    return Optional.empty();
  }

  @Override
  public ContainerBuildPlan extendContainerBuildPlan(
      ContainerBuildPlan buildPlan,
      Map<String, String> properties,
      Optional<Void> config,
      GradleData gradleData,
      ExtensionLogger logger)
      throws JibPluginExtensionException {
    // ... read jvmFlags from JibExtension, delegate to JvmFlagsLayerPlan, return updated buildPlan
  }
}
```

`JvmFlagsLayerPlan` 的 javadoc 需註明：「Builder 的 `jvmFlags` 是 `Set<String>`（lombok `@Singular`），重複的 flag 會被靜默去重 — 此為自 1.0.5 沿用的行為」（spike #3 surface 的議題，保留行為）。

## 7. Testing Strategy

| Module | Framework | 範圍 | 來源 |
|---|---|---|---|
| `core/` (source dir) | JUnit Jupiter + AssertJ + Mockito | unit test for `JvmFlagsLayerPlan`（檔案寫入、Builder defaults、separator / filename / mode 行為）— 從 maven module 既有 `JvmFlagsLayerPlanTest.java` 搬過來，package 改為 `jib.core` | 既有 test 沿用 |
| `maven/` | JUnit Jupiter + AssertJ + Mockito | 既有 `JvmFlagsExtensionTest`、`PluginConfigLocationTest` 沿用 | 既有 |
| `gradle/` | JUnit Jupiter + AssertJ + Mockito | unit test for `JvmFlagsExtension`：mock `Project` / `JibExtension`，驗證 happy path、skipIfEmpty path、property override；**不寫 integration test** | 新增（issue Not Doing 已明列只寫 unit test） |

**Test 執行歸屬**：
- `core/test` 只由 **maven 端**透過 `build-helper-maven-plugin:add-test-source` 接入並由 surefire 執行
- `gradle/test` 跑 gradle module 自己的 test，**不**重跑 core test（同一份 source / 同一份 JDK 編譯，無新增信息）

CI 必須 `mvn verify` 與 `./gradlew test` 都跑過才算 green。

## 8. Boundaries

### Always

- 任何 PR 都跑 `mvn verify` 與 `./gradlew build`，兩段都綠才能 merge
- 升級 jib extension API 時，maven 與 gradle module 同步升（讓 `provided` / `compileOnly` 的版本一致）
- `JvmFlagsLayerPlan` 行為若改變，core/test 在 maven 端跑通才算過；release notes 必須同步說明
- 動 `core/*.java` 後本地必須跑 `mvn spotless:apply`（gradle 端 check-only 會在 CI gate 上 fail 提醒）
- commit 走 Conventional Commits（release-workflow v2 要求）

### Ask first

- 升 jib extension API 大版（例如 0.4.0 → 0.5.0），可能引發 contract change
- 升 Java baseline（11 → 17/21），影響使用者
- 增加新 module（除了 maven/gradle）或新增 source dir（除了 core/）
- 修改 release-please / release-workflow 設定
- 將 core 從共享 source 升格為發布的 artifact（會反轉 [ADR-0001](../adr/0001-core-as-shared-source-not-published-artifact.md)）

### Never

- 改 `tw.com.softleader.cloud.tools.jib.maven.JvmFlagsExtension` 這個 FQN
- 改 maven module 的 `<groupId>` 或 `<artifactId>`
- 改 maven module 的 SPI service 檔路徑
- commit `gradle-api-*.jar` 或 `gradle-core-api-*.jar` 進 repo（否決方案 5.2B）
- 在 gradle module 寫 integration test（issue Not Doing 已排除）
- 為了「對稱」把 maven module 的 artifactId 從 `jib-jvm-flags-extension-maven` 改名（issue Not Doing）
- 把 core/ 變回 Maven module（會打回 B-I 結構，違反 [ADR-0001](../adr/0001-core-as-shared-source-not-published-artifact.md)）
- 順便整理依賴 / 升 Java baseline / 改 README 既有 Maven 段落（屬範圍外）

## 9. Success Criteria（MVP 7 項的可驗證條件）

每項都必須有具體可跑的驗證指令或人工檢核步驟。

### MVP-1: Repo rename ✅

- **狀態**：rename 已執行（`jib-jvm-flags-extension-maven` → `jib-jvm-flags-extension`）
- **剩下 verification 子項**：
  - `curl -sI https://github.com/softleader/jib-jvm-flags-extension-maven` 回 redirect 到新名字
  - `curl -sI https://github.com/softleader/jib-jvm-flags-extension` 回 200
  - 既有 README badges URL 改指新 repo
  - 各 `pom.xml` 的 `<scm>` / `<url>` 改指新 repo URL
  - 本地 `git remote set-url`（雖然 GitHub redirect 會接，但長期建議改）

### MVP-2: 重組 multi-module、Maven 行為不變、修 lombok AP path

- **Acceptance**：
  - root `pom.xml` 為 packaging=pom 的 aggregator，`<modules>` 只含 `maven`（**不含 `core`**，core 不是 module）
  - `maven/pom.xml` 的 `<groupId>tw.com.softleader.cloud.tools</groupId>` + `<artifactId>jib-jvm-flags-extension-maven</artifactId>` **不變**
  - `JvmFlagsExtension` FQN `tw.com.softleader.cloud.tools.jib.maven.JvmFlagsExtension` **不變**
  - `JvmFlagsLayerPlan` package 從 `jib.maven` 搬到 `jib.core`（release notes 公告 — outlier breaking）
  - SPI 服務檔 `META-INF/services/com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension` 路徑、內容**不變**
  - parent `pom.xml` 顯式宣告 `<annotationProcessorPaths>` 包含 lombok（spike R4 — 否則 JDK 25 編不過）
- **Verify**：
  - `mvn -B clean verify` 全綠（含 JDK 25 上）
  - `unzip -l maven/target/*.jar | grep -E 'JvmFlagsExtension.class|META-INF/services/'` 顯示 FQN 與服務檔路徑與 1.0.5 對齊
  - `unzip -l maven/target/*.jar | grep JvmFlagsLayerPlan` 顯示 class 在 `tw/com/softleader/cloud/tools/jib/core/`

### MVP-3: 抽 core/ 為共享 source dir

- **Acceptance**：
  - `core/` 下**沒有 pom.xml、沒有 build.gradle**，只有 `src/main/java/...JvmFlagsLayerPlan.java` 與 `src/test/java/...JvmFlagsLayerPlanTest.java`，package 為 `tw.com.softleader.cloud.tools.jib.core`
  - `maven/pom.xml` 透過 `build-helper-maven-plugin` 的 `add-source` 加 `../core/src/main/java`、`add-test-source` 加 `../core/src/test/java`
  - `maven/JvmFlagsExtension.java` import `tw.com.softleader.cloud.tools.jib.core.JvmFlagsLayerPlan`，行為相同
  - `JvmFlagsLayerPlanTest` 搬到 `core/src/test/java/.../jib.core/`，assertion 不改（package import 隨之改）
  - `PluginConfigLocation` 仍在 `maven/`
  - Spotless / license header 在兩端皆掛 `../core/src/main/java/**/*.java` 路徑
- **Verify**：
  - `mvn -B test -pl maven -am` 通過（同時 cover core test）
  - `find core/src -type f -name '*.java'` 只有 `JvmFlagsLayerPlan.java` 與其 test
  - `ls core/pom.xml core/build.gradle 2>/dev/null` 均不存在

### MVP-4: 實作 gradle module

- **Acceptance**：
  - `gradle/build.gradle` 有 `id 'java-library'` 插件，依賴 `jib-gradle-plugin-extension-api:0.4.0` + `jib-gradle-plugin:3.4.0`（compileOnly）
  - `gradle/build.gradle` 加 `sourceSets.main.java.srcDirs += '../core/src/main/java'`（**不接 test**）
  - `gradle/build.gradle` 配 `com.diffplug.spotless`（check 模式）+ Spotless 內建 `licenseHeader`，target 含 `../core/src/main/java/**/*.java` 與 `gradle/src/main/java/**/*.java`
  - `gradle/src/main/java/.../jib/gradle/JvmFlagsExtension.java` 實作 `JibGradlePluginExtension<Void>`，使用 `core.JvmFlagsLayerPlan`
  - SPI 服務檔 `META-INF/services/com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension` 內容為單行 `tw.com.softleader.cloud.tools.jib.gradle.JvmFlagsExtension`
  - 行為對等於 Maven 版：四個 property（`skipIfEmpty` / `separator` / `filename` / `mode`）的 default 值與處理邏輯完全相同；`appRoot` 預設 `/app`
  - 至少 1 個 unit test 覆蓋 happy path（`jvmFlags` 設了 → 產出 layer），1 個覆蓋 `skipIfEmpty=true` + `jvmFlags` 空的 skip 路徑
- **Verify**：
  - `cd gradle && ./gradlew build` 全綠
  - `unzip -l gradle/build/libs/*.jar | grep -E 'JvmFlagsExtension.class|JvmFlagsLayerPlan.class|META-INF/services/'` 顯示 gradle 的 JvmFlagsExtension class、core 的 JvmFlagsLayerPlan class 都在（後者 package 為 `tw/com/softleader/cloud/tools/jib/core/`）、SPI 檔對應
  - 兩個 unit test 通過
  - README 聲明相容 jib-gradle-plugin 3.4.x / 3.5.x

### MVP-5: 移除 v1 release pipeline，導入 release-workflow v2

- **Acceptance**：
  - 刪除 `Jenkinsfile-bump-version`、`Jenkinsfile-release`、`.github/workflows/bump-version.yml`、`.github/workflows/release.yml`
  - **保留 `Jenkinsfile`**（一般 build trigger，GitHub push webhook），加上 `(cd gradle && ./gradlew build)` step
  - 加上 release-please config（`.release-please-config.json` + `.release-please-manifest.json`），**unified versioning**（單一 root release，**非** `separate-pull-requests`）
  - release-please 透過 `extra-files` 同步：
    - `maven/pom.xml` `<version>`
    - `gradle/gradle.properties` `version`
  - GPG signing 在 Maven 側（central-publishing-maven-plugin + maven-gpg-plugin）與 Gradle 側（`signing` plugin）皆配置好
- **Verify**：
  - 跑 release-workflow skill 的驗證步驟
  - 在 dry-run / staging 模式（如果 skill 提供）confirm release PR 看起來合理
  - `Jenkinsfile` 在 webhook 觸發時兩端 build（maven + gradle）皆綠

### MVP-6: 第一次共版 release

- **Acceptance**：
  - `maven 1.1.0` 與 `gradle 1.1.0` **同版齊發**上 Maven Central（無 core artifact）
  - 兩個 artifact 都帶 sources jar / javadoc jar / GPG signature
  - GitHub Release 頁面對應 1 個 tag（unified versioning），release notes 包含 `JvmFlagsLayerPlan` package 從 `jib.maven` 搬到 `jib.core` 的說明
- **Verify**：
  - `curl -sI https://repo.maven.apache.org/maven2/tw/com/softleader/cloud/tools/jib-jvm-flags-extension-{maven,gradle}/1.1.0/...` 全部 200
  - Maven Central search UI 看得到 2 個 artifact
  - **無** `jib-jvm-flags-extension-core` artifact（[ADR-0001](../adr/0001-core-as-shared-source-not-published-artifact.md)）

### MVP-7: README 增加 Gradle 使用段落

- **Acceptance**：
  - 既有 Maven 段落不修改
  - 新增「## Usage with Gradle」段，含：
    - jib-gradle-plugin 設定示例（`jib { pluginExtensions { ... } }`）
    - `<implementation>` 對應 `tw.com.softleader.cloud.tools.jib.gradle.JvmFlagsExtension`
    - 四個 properties (`skipIfEmpty` / `separator` / `filename` / `mode`) 與 Maven 段相同說明
    - 相容性聲明：jib-gradle-plugin 3.4.x / 3.5.x
- **Verify**：
  - 人工 review README diff
  - Gradle 範例若可，跑一次 e2e build container 驗證 layer 生成（可選）

## 10. Open Questions — 全部 closed

- ~~OQ-1~~：jib-gradle-plugin compileOnly = **3.4.0**；README 聲明相容 3.4.x / 3.5.x
- ~~OQ-2~~：CI 用**單 job 串接** — `mvn -B verify && (cd gradle && ./gradlew build)`
- ~~OQ-3~~：release-please **unified versioning**（單一 root release，非 separate-pull-requests）+ `extra-files` 同步 `maven/pom.xml` `<version>` 與 `gradle/gradle.properties` `version`
- ~~OQ-4~~：Gradle 端用 `com.diffplug.spotless`（含內建 `licenseHeader` step），**不引入** `com.github.hierynomus.license`
- ~~OQ-5~~：**保留 `Jenkinsfile`**（公司 Jenkins 仍透過 GitHub push webhook 跑此 repo 的一般 build），加 `(cd gradle && ./gradlew build)` step；`Jenkinsfile-bump-version` / `Jenkinsfile-release` 一併刪除

## 11. Open Risks（issue 已列 + spike 補充）

- **R1（issue）**：kapok#731 流產 → Gradle module 變 dead weight。緩解：Gradle module CI 跑 unit test，至少防止 silent rot。
- ~~R2（issue）~~：release-please 多 package PR 翻倍 — **消失**：unified versioning 之後不再有此問題。
- **R3（spike 補充）**：`org.gradle.api.Project` 是 Gradle 內部 API，未來 Gradle 版本變動可能 break。緩解：CI 跑 unit test、訂版本相容矩陣（README 聲明 3.4.x / 3.5.x，未來新大版手動評估）。
- ~~R4（spike 補充）~~：lombok annotation processor path **升格進 MVP-2 acceptance**，不再是 risk。
- **R5（grilling 新增）**：兩端 spotless 在 `core/*.java` 上產生 byte 差異 → CI lint fail。緩解：`google-java-format` 版本 pin 到 properties 雙端共用；gradle 端 spotless 設 check-only 避免互打。

## 12. Out of Scope（明確排除）

繼承 issue 的 Not Doing：

- 不引入 Gradle 為**整 repo**的 build tool（Maven aggregator + Gradle module 局部）
- 不改 maven module artifactId
- 不為 Gradle module 寫 integration test
- 不為 core 抽介面 + 實作分層
- 不順手做 Java baseline 升版、依賴大整理
- 不改既有 Maven 段落，只新增 Gradle section

額外排除（grilling 補充）：

- **不發 `jib-jvm-flags-extension-core` 為獨立 Maven Central artifact**（[ADR-0001](../adr/0001-core-as-shared-source-not-published-artifact.md)）
- 不嘗試發 `jib-jvm-flags-extension-gradle` 到 Gradle Plugin Portal（Maven Central 即可，使用者透過 jib-gradle-plugin 的 dependencies 引入）
- **不引入額外的 license-header plugin**（用 Spotless 內建的 `licenseHeader` step；`license-maven-plugin` 與 `com.github.hierynomus.license` 都不用）
- **不採用各 artifact 獨立版號**（unified versioning，兩端齊發；見 §1 Success #3）

---

## Appendix A: Spike 驗證的 build artifacts

`~/code/spike-jib-gradle-extension/` 已實證：

```
[INFO] jib-jvm-flags-extension-parent ..................... SUCCESS
[INFO] jib-jvm-flags-extension-core ....................... SUCCESS
[INFO] jib-jvm-flags-extension-maven ...................... SUCCESS
[INFO] jib-jvm-flags-extension-gradle ..................... SUCCESS
```

注意：

1. spike 用 Maven aggregator + system-scoped gradle-api 編 gradle module（純驗證 type 對得上）。實作時 gradle module 改用 Gradle build（決議 5.2A）。
2. spike core/ **是** Maven module 並 install 到 mavenLocal（B-I 結構）；本 spec **改用 B-II**（core/ 不是 module，shared source 直接接入），spike 不直接複製到正式 repo。

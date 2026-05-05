# Plan: Monorepo Restructure + Gradle Module

| | |
|---|---|
| Spec | [2026-05-monorepo-and-gradle](./2026-05-monorepo-and-gradle.md) |
| ADR | [0001 — core as shared source](../adr/0001-core-as-shared-source-not-published-artifact.md) |
| Date | 2026-05-04 |

---

## PR 切分

把 7 個 MVP 拆 4 個 PR + 1 個 release 動作，順序 sequential。中間狀態都保持 buildable / mergeable。

```
MVP-1 (done) ─┐
              ▼
        ┌───────────────────────────────────┐
        │ PR-A: 重組 multi-module + core/   │  MVP-2 + MVP-3 合併
        │ + lombok AP fix                   │  （邏輯上是「同一場重組」）
        └───────────────────────────────────┘
                       │
                       ▼
        ┌───────────────────────────────────┐
        │ PR-B: gradle module               │  MVP-4
        └───────────────────────────────────┘
                       │
                       ▼
        ┌───────────────────────────────────┐
        │ PR-C: README + Jenkinsfile        │  MVP-7 + Jenkinsfile 加 gradle step
        └───────────────────────────────────┘
                       │
                       ▼
        ┌───────────────────────────────────┐
        │ PR-D: release-workflow v2         │  MVP-5（呼叫 release-workflow skill）
        └───────────────────────────────────┘
                       │
                       ▼
              Release 1.1.0 齊發              MVP-6（合 release-please PR）
```

### 為何 MVP-2 + MVP-3 合一個 PR

獨立做 MVP-2 的話，maven module 仍是 monolithic — 只搬資料夾位置、加 lombok AP fix，沒抽 core，使用者升級沒感。中間狀態不帶來任何用戶價值，只增加 review 負擔。合 PR 反而是「一場完整的重組」，FQN 變動（`JvmFlagsLayerPlan` from `jib.maven` to `jib.core`）一次發生、release notes 一次說清。

### 為何 PR-B / PR-C / PR-D 分開

- PR-B 純加新 module，maven 端零改動 — review focal point 完全在 gradle 設定/實作
- PR-C README + Jenkinsfile：兩個都是「敘事/CI」改動，跟 code 邏輯無關，併在一起 review burden 不增
- PR-D release-workflow 切換是高風險獨立 concern，必須在 build 結構穩定後做，且需要 release-workflow skill 介入（不是普通 PR review 流程）

## 各 PR Entry / Exit Criteria

### PR-A: 重組 multi-module + core/ + lombok AP fix

**Entry：**
- spec + ADR-0001 已 commit 到 main（或本 PR 一併 commit）
- 本地 `git remote set-url`（非阻塞，可後做）

**Touched files：**
- 新增：`pom.xml` (root aggregator)、`maven/pom.xml`、`core/src/main/java/.../jib/core/JvmFlagsLayerPlan.java`、`core/src/test/java/.../jib/core/JvmFlagsLayerPlanTest.java`
- 移動：`src/main/java/.../jib/maven/{JvmFlagsExtension,PluginConfigLocation}.java` → `maven/src/main/java/.../jib/maven/`
- 移動：`src/main/resources/META-INF/services/...` → `maven/src/main/resources/META-INF/services/...`
- 移動：`src/test/java/.../jib/maven/{JvmFlagsExtensionTest,PluginConfigLocationTest}.java` → `maven/src/test/...`
- 刪除：原 `src/`
- 修改：`Jenkinsfile`（更新 working dir 或 mvn 命令路徑，**先不加 gradle step**，那是 PR-C）
- 改動：`maven/pom.xml` 的 `<groupId>` / `<artifactId>` / `<version>` 不變；package 與服務檔不變

**Exit（mvn verify 必須綠）：**
- `mvn -B clean verify` 從 root 跑 SUCCESS（含 lombok AP 顯式宣告）
- `unzip -l maven/target/jib-jvm-flags-extension-maven-*.jar` 顯示：
  - `tw/com/softleader/cloud/tools/jib/maven/JvmFlagsExtension.class`（FQN 不變）
  - `tw/com/softleader/cloud/tools/jib/maven/PluginConfigLocation.class`
  - `tw/com/softleader/cloud/tools/jib/core/JvmFlagsLayerPlan.class`（從 `jib.maven` 搬到 `jib.core`）
  - `META-INF/services/com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension`（路徑/內容不變）
- 既有 3 個 test 全過（其中 `JvmFlagsLayerPlanTest` 在新位置 `core/src/test/`，由 maven 端 surefire 透過 `add-test-source` 跑）
- `find core -name pom.xml -o -name build.gradle` 為空（core/ 不是 module）
- `git remote -v` 指新 repo 名（若已執行）

**主要風險：**
- `build-helper-maven-plugin` 的 `add-source` / `add-test-source` 配置首次接觸，可能誤把 core/ test 列為 main、或 surefire 沒抓到 test classes path
- spotless `<includes>` 要顯式列 `../core/src/main/java/**/*.java`，不然 core source 不會被 format 檢查
- license header 同上
- root pom.xml 的 `pluginManagement` 要把 lombok AP path 給 maven module 繼承

### PR-B: gradle module

**Entry：**
- PR-A merged 到 main

**Touched files：**
- 新增：`gradle/build.gradle`、`gradle/settings.gradle`、`gradle/gradle.properties`、`gradle/gradlew`、`gradle/gradlew.bat`、`gradle/gradle/wrapper/*`
- 新增：`gradle/src/main/java/.../jib/gradle/JvmFlagsExtension.java`
- 新增：`gradle/src/main/resources/META-INF/services/com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension`
- 新增：`gradle/src/test/java/.../jib/gradle/JvmFlagsExtensionTest.java`
- 修改：`.gitignore`（加 `gradle/build/`、`gradle/.gradle/` 等）

**Exit：**
- `(cd gradle && ./gradlew build)` SUCCESS
- `unzip -l gradle/build/libs/*.jar` 顯示：
  - `tw/com/softleader/cloud/tools/jib/gradle/JvmFlagsExtension.class`
  - `tw/com/softleader/cloud/tools/jib/core/JvmFlagsLayerPlan.class`（**在 gradle jar 內**，由 sourceSets 接入 core 後編譯）
  - `META-INF/services/com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension`
- ≥2 個 unit test 通過：happy path（`jvmFlags` 設了 → 產出 layer），skip path（`skipIfEmpty=true` + `jvmFlags` 空）
- `mvn -B clean verify` 仍綠（gradle module 不影響 maven 側）
- spotless / license header 在 gradle 端跑通，同一份 `core/JvmFlagsLayerPlan.java` 在 maven 與 gradle 兩端 byte-identical（驗：`diff <(unzip -p maven/target/*.jar 'tw/...JvmFlagsLayerPlan.class') <(unzip -p gradle/build/libs/*.jar 'tw/...JvmFlagsLayerPlan.class')` 為空）

**主要風險：**
- gradle wrapper version 選擇（建議 8.5，跟既有本機 SDKMAN 一致；spec 沒鎖死，可調）
- spotless 兩端 google-java-format 版本若不一致，core class 會 byte 差異 → CI fail（R5）
- `JibExtension` / `ContainerParameters` 的 import 來自 `jib-gradle-plugin:3.4.0`（compileOnly + 加 Gradle Plugin Portal repository）
- `Project.getBuildDir()` 在 Gradle 8 deprecated；spec 的 sample 用了它，實作要改用 `getLayout().getBuildDirectory()` 並驗證 jib-gradle-plugin 3.4 仍 compatible

### PR-C: README + Jenkinsfile gradle step

**Entry：**
- PR-B merged

**Touched files：**
- 修改：`README.md`（新增「## Usage with Gradle」section，maven 段不動）
- 修改：`Jenkinsfile`（加 `(cd gradle && ./gradlew build)` step）

**Exit：**
- 人工 review README diff
- Jenkinsfile push 觸發後 maven build + gradle build 兩段都綠（在 staging branch 驗）

**主要風險：**
- Jenkinsfile 的 build agent 環境有沒有 Java 11+ 給 Gradle wrapper 用（通常有）
- README badges URL 已在 PR-A 時更新到新 repo 名（如沒，PR-C 補）

### PR-D: release-workflow v2

**Entry：**
- PR-C merged
- 確認 release-workflow skill 對「unified versioning + 兩個 artifact + 兩種 build tool publish」場景的支援度

**Touched files（依 release-workflow skill 規範）：**
- 刪除：`Jenkinsfile-bump-version`、`Jenkinsfile-release`、`.github/workflows/bump-version.yml`、`.github/workflows/release.yml`
- 新增：`.release-please-config.json`、`.release-please-manifest.json`、`.github/workflows/release-please.yml`（或 skill 提供的）
- 修改：`maven/pom.xml`（如需 `central-publishing-maven-plugin` 配置調整 — 既有 `release` profile 已就緒，僅可能需要小調）
- 修改：`gradle/build.gradle`（加 `maven-publish` plugin + `signing` plugin + Maven Central staging URL，依 skill 規範）

**Exit：**
- release-workflow skill 的驗證步驟通過
- 在 staging branch 推一個 conventional commit，看 release-please 開出的 candidate PR：
  - 包含 `maven/pom.xml` `<version>` 與 `gradle/gradle.properties` `version` 同步 bump 到候選版本
  - 單一 PR 而非兩個（unified versioning）
  - GitHub Release notes 內容合理

**主要風險：**
- Maven Central 的 GPG key 在新 release pipeline 設定要重新 wire（`gpg.keyname` / passphrase server id）
- Gradle 端 publish 到 Maven Central 的細節（staging URL、metadata pom.xml override）— 要對照 jib-extensions 第一方 gradle module 的設定學習
- release-please 對 `gradle.properties` 的 `version=` 行 regex 替換要驗

### Release 動作（MVP-6）

**Entry：**
- PR-D merged
- release-please 開出第一個 candidate PR，candidate 版本為 1.1.0

**動作：**
- review release notes 確保提到 `JvmFlagsLayerPlan` package 從 `jib.maven` 搬到 `jib.core`
- merge candidate PR
- pipeline 自動 trigger maven publish + gradle publish 兩條
- 監看兩條 publish 都成功

**Exit（spec MVP-6 acceptance）：**
- `curl -sI https://repo.maven.apache.org/maven2/tw/com/softleader/cloud/tools/jib-jvm-flags-extension-{maven,gradle}/1.1.0/...` 全部 200
- Maven Central search UI 看得到兩個 1.1.0 artifact
- **無** `jib-jvm-flags-extension-core` artifact

## 並行性

- PR-A → PR-B → PR-C 嚴格 sequential（依賴鏈）
- PR-C 的 README 內容**草稿**可以在 PR-B 進行中平行起草（內容不依賴 PR-B 的最終 byte，只依賴 spec 的 §6 範例 + spec MVP-7 acceptance），但 PR 開立要等 PR-B merge
- PR-D 嚴格在 PR-C 後做（release-please 設定要對齊穩定的 build 結構）

## Verification Checkpoints

每個 PR 必須通過：

```
mvn -B clean verify                                # 從 PR-A 起
(cd gradle && ./gradlew build) || true             # 從 PR-B 起，PR-A 時 gradle/ 不存在
```

CI gate（spec §8 Always）：兩段都綠才能 merge。PR-A 時可暫只跑 `mvn`。

額外 gate：
- core class byte-identical 跨兩 jar（PR-B 起）
- spotless / license check 跨兩端（PR-B 起）

## Risk Mitigation Summary

| Risk | 觸發 PR | 緩解 |
|---|---|---|
| build-helper 配置誤 | PR-A | 對照本 plan exit checklist 逐項驗 |
| spotless 兩端 byte 差異 (R5) | PR-B | google-java-format 版本 pin 到 root pom property，gradle build.gradle 引用同 property；gradle 端 check-only |
| `JibExtension` import 失敗 | PR-B | 加 Gradle Plugin Portal repo + compileOnly `jib-gradle-plugin:3.4.0`；參考本機 `~/code/github.com/GoogleContainerTools/jib-extensions/first-party/jib-native-image-extension-gradle/` |
| Gradle 8 deprecation (`getBuildDir`) | PR-B | 用 `getLayout().getBuildDirectory()` |
| release-please unified versioning 設定 | PR-D | 走 release-workflow skill；先在 staging branch 驗 candidate PR |
| Maven Central GPG signing 對 gradle 端 | PR-D | 對照 jib-extensions 官方 gradle module publish 設定 |
| kapok#731 流產 (R1) | 全程 | gradle module CI 至少跑 unit test，避免 silent rot |

## 開工動作（PR-A 啟動前）

1. spec + ADR + plan 三份文件 commit 到 main（或併入 PR-A 第一個 commit）
2. 本地 `git remote set-url origin git@github.com:softleader/jib-jvm-flags-extension.git`（非阻塞）
3. 創 feature branch `feat/monorepo-restructure-pr-a` 啟動 PR-A

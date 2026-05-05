/*
 * Copyright © 2024 SoftLeader
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package tw.com.softleader.cloud.tools.jib.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class JvmFlagsLayerPlanTest {

  @Mock private ExtensionLogger logger;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testCreateLayerWithJvmFlags(@TempDir Path tempDir) throws IOException {
    Set<String> jvmFlags = Set.of("-Xmx512m", "-Djava.security.egd=file:/dev/./urandom");

    JvmFlagsLayerPlan plan =
        JvmFlagsLayerPlan.builder().buildDir(tempDir).jvmFlags(jvmFlags).logger(logger).build();

    FileEntriesLayer layer = plan.create(AbsoluteUnixPath.get("/app"));

    assertThat(JvmFlagsLayerPlan.LAYER_JVM_FLAGS).isEqualTo(layer.getName());
    assertThat(layer.getEntries()).hasSize(1);
    assertThat(
            tempDir
                .resolve(JvmFlagsLayerPlan.CACHE_DIRECTORY_NAME)
                .resolve(JvmFlagsLayerPlan.JIB_JVM_FLAGS_FILE))
        .exists();
  }

  @Test
  void testWriteFileConservativelyCreatesFile(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("test-file");
    String content = "test-content";

    JvmFlagsLayerPlan plan = JvmFlagsLayerPlan.builder().build();
    plan.writeFileConservatively(file, content);

    assertThat(file).exists().hasContent(content);
  }

  @Test
  void testWriteFileConservativelyNoOverwrite(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("test-file");
    String content = "test-content";

    Files.writeString(file, content);

    JvmFlagsLayerPlan plan = JvmFlagsLayerPlan.builder().build();
    plan.writeFileConservatively(file, "new-content");

    assertThat(file).exists().content().isNotEqualTo(content);
  }
}

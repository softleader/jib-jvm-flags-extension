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
package tw.com.softleader.cloud.tools.jib.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tw.com.softleader.cloud.tools.jib.gradle.JvmFlagsExtension.*;

import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.gradle.ContainerParameters;
import com.google.cloud.tools.jib.gradle.JibExtension;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.ExtensionContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tw.com.softleader.cloud.tools.jib.core.JvmFlagsLayerPlan;

class JvmFlagsExtensionTest {

  @Mock private Project project;
  @Mock private ProjectLayout projectLayout;
  @Mock private DirectoryProperty buildDirectoryProperty;
  @Mock private Directory buildDirectory;
  @Mock private ExtensionContainer extensionContainer;
  @Mock private JibExtension jibExtension;
  @Mock private ContainerParameters containerParameters;
  @Mock private ExtensionLogger logger;

  private GradleData gradleData;
  private JvmFlagsExtension extension;

  @BeforeEach
  void setUp(@TempDir Path tempDir) {
    MockitoAnnotations.openMocks(this);
    extension = new JvmFlagsExtension();
    gradleData = () -> project;
    when(project.getLayout()).thenReturn(projectLayout);
    when(projectLayout.getBuildDirectory()).thenReturn(buildDirectoryProperty);
    when(buildDirectoryProperty.get()).thenReturn(buildDirectory);
    when(buildDirectory.getAsFile()).thenReturn(tempDir.toFile());
    when(project.getExtensions()).thenReturn(extensionContainer);
  }

  @Test
  void testExtendContainerBuildPlanWithNoJvmFlags() throws JibPluginExtensionException {
    when(extensionContainer.findByType(JibExtension.class)).thenReturn(null);

    ContainerBuildPlan originalPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan modifiedPlan =
        extension.extendContainerBuildPlan(
            originalPlan,
            Map.of(PROPERTY_SKIP_IF_EMPTY, "true"),
            Optional.empty(),
            gradleData,
            logger);

    assertThat(originalPlan).isEqualTo(modifiedPlan);
  }

  @Test
  void testExtendContainerBuildPlanWithOnlyBlankJvmFlagsAndSkipIfEmpty()
      throws JibPluginExtensionException {
    when(extensionContainer.findByType(JibExtension.class)).thenReturn(jibExtension);
    when(jibExtension.getContainer()).thenReturn(containerParameters);
    when(containerParameters.getJvmFlags()).thenReturn(List.of("  ", ""));

    ContainerBuildPlan originalPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan modifiedPlan =
        extension.extendContainerBuildPlan(
            originalPlan,
            Map.of(PROPERTY_SKIP_IF_EMPTY, "true"),
            Optional.empty(),
            gradleData,
            logger);

    assertThat(originalPlan).isEqualTo(modifiedPlan);
  }

  @Test
  void testExtendContainerBuildPlanWithJvmFlags() throws JibPluginExtensionException {
    when(extensionContainer.findByType(JibExtension.class)).thenReturn(jibExtension);
    when(jibExtension.getContainer()).thenReturn(containerParameters);
    when(containerParameters.getJvmFlags())
        .thenReturn(List.of("-Xmx512m", "-Djava.security.egd=file:/dev/./urandom"));
    when(containerParameters.getAppRoot()).thenReturn("");

    ContainerBuildPlan originalPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan modifiedPlan =
        extension.extendContainerBuildPlan(
            originalPlan, Map.of(), Optional.empty(), gradleData, logger);

    assertThat(originalPlan).isNotEqualTo(modifiedPlan);
    List<? extends LayerObject> layers = modifiedPlan.getLayers();
    assertThat(layers).hasSize(1);

    FileEntriesLayer layer = (FileEntriesLayer) layers.get(0);
    assertThat(JvmFlagsLayerPlan.LAYER_JVM_FLAGS).isEqualTo(layer.getName());
  }

  @Test
  void testSkipIfEmptyWithEmptyProperties() {
    assertThat(isSkipIfEmpty(Map.of())).isSameAs(DEFAULT_SKIP_IF_EMPTY);
  }

  @Test
  void testSkipIfEmptyWithTrueValue() {
    assertThat(isSkipIfEmpty(Map.of(PROPERTY_SKIP_IF_EMPTY, "true"))).isTrue();
  }

  @Test
  void testSkipIfEmptyWithFalseValue() {
    assertThat(isSkipIfEmpty(Map.of(PROPERTY_SKIP_IF_EMPTY, "false"))).isFalse();
  }

  @Test
  void testSkipIfEmptyWithYesValue() {
    assertThat(isSkipIfEmpty(Map.of(PROPERTY_SKIP_IF_EMPTY, "yes"))).isTrue();
  }

  @Test
  void testSkipIfEmptyWithInvalidValue() {
    assertThat(isSkipIfEmpty(Map.of(PROPERTY_SKIP_IF_EMPTY, "invalid")))
        .isEqualTo(DEFAULT_SKIP_IF_EMPTY);
  }

  @Test
  void testSkipIfEmptyWithNullValue() {
    var properties = new HashMap<String, String>();
    properties.put(PROPERTY_SKIP_IF_EMPTY, null);
    assertThat(isSkipIfEmpty(properties)).isEqualTo(DEFAULT_SKIP_IF_EMPTY);
  }

  @Test
  void testSeparatorWithEmptyProperties() {
    assertThat(getSeparator(Map.of())).isEmpty();
  }

  @Test
  void testSeparatorWithNonNullValue() {
    assertThat(getSeparator(Map.of(PROPERTY_SEPARATOR, ", "))).isPresent().get().isEqualTo(", ");
  }

  @Test
  void testSeparatorWithNullValue() {
    var properties = new HashMap<String, String>();
    properties.put(PROPERTY_SEPARATOR, null);
    assertThat(getSeparator(properties)).isEmpty();
  }
}

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
package tw.com.softleader.cloud.tools.jib.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tw.com.softleader.cloud.tools.jib.maven.JvmFlagsExtension.*;

import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tw.com.softleader.cloud.tools.jib.core.JvmFlagsLayerPlan;

class JvmFlagsExtensionTest {
  @Mock private MavenData mavenData;

  @Mock private MavenProject mavenProject;

  @Mock private Build build;

  @Mock private ExtensionLogger logger;

  private JvmFlagsExtension extension;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    extension = new JvmFlagsExtension();
    when(mavenData.getMavenProject()).thenReturn(mavenProject);
    when(mavenProject.getBuild()).thenReturn(build);
  }

  @Test
  void testExtendContainerBuildPlanWithNoJvmFlags(@TempDir Path tempDir)
      throws JibPluginExtensionException {
    when(mavenProject.getBuild().getDirectory()).thenReturn(tempDir.toString());
    when(mavenProject.getPlugin("com.google.cloud.tools:jib-maven-plugin")).thenReturn(null);

    ContainerBuildPlan originalPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan modifiedPlan =
        extension.extendContainerBuildPlan(
            originalPlan,
            Map.of(JvmFlagsExtension.PROPERTY_SKIP_IF_EMPTY, "true"),
            Optional.empty(),
            mavenData,
            logger);

    assertThat(originalPlan).isEqualTo(modifiedPlan);
  }

  @Test
  void testExtendContainerBuildPlanWithJvmFlags(@TempDir Path tempDir)
      throws JibPluginExtensionException {
    when(mavenProject.getBuild().getDirectory()).thenReturn(tempDir.toString());
    Plugin jibPlugin = mock(Plugin.class);
    when(mavenProject.getPlugin(JIB_MAVEN_PLUGIN_ID)).thenReturn(jibPlugin);

    Xpp3Dom configurationDom = new Xpp3Dom("configuration");
    Xpp3Dom containerDom = new Xpp3Dom("container");
    Xpp3Dom jvmFlagsDom = new Xpp3Dom("jvmFlags");
    Xpp3Dom flag1 = new Xpp3Dom("jvmFlag");
    flag1.setValue("-Xmx512m");
    Xpp3Dom flag2 = new Xpp3Dom("jvmFlag");
    flag2.setValue("-Djava.security.egd=file:/dev/./urandom");

    jvmFlagsDom.addChild(flag1);
    jvmFlagsDom.addChild(flag2);
    containerDom.addChild(jvmFlagsDom);
    configurationDom.addChild(containerDom);

    when(jibPlugin.getConfiguration()).thenReturn(configurationDom);

    ContainerBuildPlan originalPlan = ContainerBuildPlan.builder().build();
    ContainerBuildPlan modifiedPlan =
        extension.extendContainerBuildPlan(
            originalPlan, Map.of(), Optional.empty(), mavenData, logger);

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

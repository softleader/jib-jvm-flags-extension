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

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginConfigLocationTest {

  private MavenProject project;
  private Plugin plugin;
  private Xpp3Dom configuration;
  private PluginConfigLocation pluginConfigLocation;

  @BeforeEach
  public void setUp() {
    project = mock(MavenProject.class);
    plugin = mock(Plugin.class);
    configuration = mock(Xpp3Dom.class);
    pluginConfigLocation =
        PluginConfigLocation.builder()
            .pluginId("test-plugin")
            .domPath("root")
            .domPath("child")
            .build();
  }

  @Test
  public void testGetValue_PluginNotFound() {
    when(project.getPlugin("test-plugin")).thenReturn(null);
    assertThat(pluginConfigLocation.getValue(project)).isEmpty();
  }

  @Test
  public void testGetValue_ConfigurationNotFound() {
    when(project.getPlugin("test-plugin")).thenReturn(plugin);
    when(plugin.getConfiguration()).thenReturn(null);
    assertThat(pluginConfigLocation.getValue(project)).isEmpty();
  }

  @Test
  public void testGetValue_NodeNotFound() {
    when(project.getPlugin("test-plugin")).thenReturn(plugin);
    when(plugin.getConfiguration()).thenReturn(configuration);
    when(configuration.getChild("root")).thenReturn(null);
    assertThat(pluginConfigLocation.getValue(project)).isEmpty();
  }

  @Test
  public void testGetValue_Success() {
    Xpp3Dom rootNode = mock(Xpp3Dom.class);
    Xpp3Dom childNode = mock(Xpp3Dom.class);
    when(project.getPlugin("test-plugin")).thenReturn(plugin);
    when(plugin.getConfiguration()).thenReturn(configuration);
    when(configuration.getChild("root")).thenReturn(rootNode);
    when(rootNode.getChild("child")).thenReturn(childNode);
    when(childNode.getValue()).thenReturn("expectedValue");
    assertThat(pluginConfigLocation.getValue(project)).isPresent().get().isEqualTo("expectedValue");
  }
}

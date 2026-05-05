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

import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Represents a location of a plugin configuration value in POM.
 *
 * @author Matt Ho
 */
@Value
@Builder(builderClassName = "Builder", toBuilder = true)
public class PluginConfigLocation {

  @NonNull String pluginId;
  @Singular List<String> domPaths;

  public static final class Builder {}

  public Optional<String> getValue(@NonNull MavenProject project) {
    return getDom(project).map(Xpp3Dom::getValue);
  }

  public Optional<Xpp3Dom> getDom(@NonNull MavenProject project) {
    Plugin plugin = project.getPlugin(pluginId);
    if (plugin == null) {
      return Optional.empty();
    }
    return getDom((Xpp3Dom) plugin.getConfiguration(), domPaths);
  }

  private Optional<Xpp3Dom> getDom(Xpp3Dom dom, List<String> nodePath) {
    if (dom == null) {
      return Optional.empty();
    }
    Xpp3Dom node = dom;
    for (String child : nodePath) {
      node = node.getChild(child);
      if (node == null) {
        return Optional.empty();
      }
    }
    return Optional.of(node);
  }
}

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

import static com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel.LIFECYCLE;
import static com.google.common.base.Verify.verifyNotNull;
import static java.lang.Boolean.FALSE;
import static java.util.Optional.ofNullable;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.gradle.JibExtension;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import tw.com.softleader.cloud.tools.jib.core.JvmFlagsLayerPlan;

/**
 * JVM flags extension for Jib Gradle Plugin.
 *
 * @author Matt Ho
 */
public class JvmFlagsExtension implements JibGradlePluginExtension<Void> {

  public static final String PROPERTY_SKIP_IF_EMPTY = "skipIfEmpty";
  public static final boolean DEFAULT_SKIP_IF_EMPTY = FALSE;
  public static final String PROPERTY_SEPARATOR = "separator";
  public static final String PROPERTY_FILENAME = "filename";
  public static final String PROPERTY_MODE = "mode";

  static final String DEFAULT_APP_ROOT = "/app";

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
    logger.log(LIFECYCLE, "Running JVM Flags Jib extension");
    try {
      Project project = gradleData.getProject();
      var jvmFlags = getJvmFlags(project);
      if (jvmFlags.isEmpty() && isSkipIfEmpty(properties)) {
        logger.log(LIFECYCLE, "No JVM Flags are configured, skipping");
        return buildPlan;
      }
      JvmFlagsLayerPlan.Builder plan =
          JvmFlagsLayerPlan.builder()
              .logger(logger)
              .buildDir(project.getLayout().getBuildDirectory().get().getAsFile().toPath())
              .jvmFlags(jvmFlags);
      getSeparator(properties).ifPresent(plan::separator);
      getFilename(properties).map(StringUtils::trimToNull).ifPresent(plan::filename);
      getMode(properties).map(StringUtils::trimToNull).ifPresent(plan::mode);
      FileEntriesLayer layer = plan.build().create(getAppRootPath(project));
      return buildPlan.toBuilder().addLayer(layer).build();
    } catch (IOException ex) {
      throw new JibPluginExtensionException(getClass(), verifyNotNull(ex.getMessage()), ex);
    }
  }

  private AbsoluteUnixPath getAppRootPath(Project project) {
    return AbsoluteUnixPath.get(
        ofNullable(getJibExtension(project))
            .map(JibExtension::getContainer)
            .map(container -> container.getAppRoot())
            .filter(StringUtils::isNotBlank)
            .orElse(DEFAULT_APP_ROOT));
  }

  private List<String> getJvmFlags(Project project) {
    return ofNullable(getJibExtension(project))
        .map(JibExtension::getContainer)
        .map(container -> container.getJvmFlags())
        .map(
            jvmFlags ->
                jvmFlags.stream().filter(StringUtils::isNotBlank).collect(Collectors.toList()))
        .orElseGet(Collections::emptyList);
  }

  @VisibleForTesting
  static JibExtension getJibExtension(Project project) {
    return project.getExtensions().findByType(JibExtension.class);
  }

  @VisibleForTesting
  static Optional<String> getMode(@NonNull Map<String, String> properties) {
    return ofNullable(properties.get(PROPERTY_MODE)).filter(StringUtils::isNotBlank);
  }

  @VisibleForTesting
  static Optional<String> getFilename(@NonNull Map<String, String> properties) {
    return ofNullable(properties.get(PROPERTY_FILENAME)).filter(StringUtils::isNotBlank);
  }

  @VisibleForTesting
  static Optional<String> getSeparator(@NonNull Map<String, String> properties) {
    return ofNullable(properties.get(PROPERTY_SEPARATOR));
  }

  @VisibleForTesting
  static boolean isSkipIfEmpty(@NonNull Map<String, String> properties) {
    return ofNullable(properties.get(PROPERTY_SKIP_IF_EMPTY))
        .map(BooleanUtils::toBoolean)
        .orElse(DEFAULT_SKIP_IF_EMPTY);
  }
}

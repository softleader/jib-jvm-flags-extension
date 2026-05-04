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

import static com.google.cloud.tools.jib.api.buildplan.FilePermissions.DEFAULT_FILE_PERMISSIONS;
import static com.google.cloud.tools.jib.api.buildplan.FilePermissions.fromOctalString;
import static com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel.DEBUG;
import static com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel.LIFECYCLE;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.lang.System.getProperty;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.ToString;

/**
 * JVM flags layer plan
 *
 * @author Matt Ho
 */
@ToString
@Builder(builderClassName = "Builder", toBuilder = true)
public class JvmFlagsLayerPlan {

  public static final String LAYER_JVM_FLAGS = "jvm flags";
  public static final String JIB_JVM_FLAGS_FILE = "jib-jvm-flags-file";
  public static final String CACHE_DIRECTORY_NAME = "jib-cache";

  @ToString.Exclude private final ExtensionLogger logger;

  @NonNull @lombok.Builder.Default
  private final Path buildDir = Paths.get(getProperty("java.io.tmpdir"));

  @Singular private final Set<String> jvmFlags;
  @NonNull @lombok.Builder.Default private final String separator = " ";
  @NonNull @lombok.Builder.Default private final String filename = JIB_JVM_FLAGS_FILE;

  @NonNull @lombok.Builder.Default
  private final String mode = DEFAULT_FILE_PERMISSIONS.toOctalString();

  public static final class Builder {}

  /**
   * Create a layer containing the configured `jvmFlags` file
   *
   * @param directoryInContainer directory will be contained jvm flags file in the container
   * @return jvm flags layer
   */
  public FileEntriesLayer create(@NonNull AbsoluteUnixPath directoryInContainer)
      throws IOException {
    log(DEBUG, "Creating '%s' layer with %s", LAYER_JVM_FLAGS, this);
    Path sourcePath = createJvmFlagsFile();
    AbsoluteUnixPath pathInContainer = directoryInContainer.resolve(filename);
    log(LIFECYCLE, "Adding layer containing '%s' file to the image", pathInContainer.toString());
    return FileEntriesLayer.builder()
        .setName(LAYER_JVM_FLAGS)
        .addEntry(sourcePath, pathInContainer, fromOctalString(mode))
        .build();
  }

  private Path createJvmFlagsFile() throws IOException {
    var content = join(separator, jvmFlags);
    log(LIFECYCLE, "JVM Flags configured: [%s]", content);
    var path = buildDir.resolve(CACHE_DIRECTORY_NAME).resolve(filename);
    writeFileConservatively(path, content);
    return path;
  }

  @VisibleForTesting
  void writeFileConservatively(@NonNull Path file, @NonNull String content) throws IOException {
    if (Files.notExists(file) || !Files.readString(file).equals(content)) {
      Files.createDirectories(file.getParent());
      Files.write(file, content.getBytes(UTF_8));
    }
  }

  private void log(LogLevel level, String message, Object... args) {
    if (logger != null) {
      logger.log(level, format(message, args));
    }
  }
}

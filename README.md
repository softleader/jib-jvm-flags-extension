[![version](https://img.shields.io/github/v/release/softleader/jib-jvm-flags-extension-maven?color=brightgreen&sort=semver)](https://github.com/softleader/jib-jvm-flags-extension-maven/releases/latest)
[![Maven Central](https://img.shields.io/maven-central/v/tw.com.softleader.cloud.tools/jib-jvm-flags-extension-maven?color=orange)](https://central.sonatype.com/search?q=g%3Atw.com.softleader.cloud.tools&smo=true&namespace=tw.com.softleader.cloud.tools)
![GitHub tag checks state](https://img.shields.io/github/checks-status/softleader/jib-jvm-flags-extension-maven/main)
![GitHub issues](https://img.shields.io/github/issues-raw/softleader/jib-jvm-flags-extension-maven)

# Jib JVM Flags Extension

A [Jib](https://github.com/GoogleContainerTools/jib) [extension](https://github.com/GoogleContainerTools/jib-extensions) for Maven and Gradle that preserves your `jvmFlags` when you switch to a [custom entrypoint](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#custom-container-entrypoint).

## Why?

Jib silently drops `jvmFlags` whenever a custom `entrypoint` is set. This extension keeps them around by writing the configured flags to `/app/jib-jvm-flags-file` inside the image, so your entrypoint can apply them at startup:

```sh
# Java 11+ argument file
java @/app/jib-jvm-flags-file -cp @/app/jib-classpath-file @/app/jib-main-class-file

# Or via shell
java $(cat /app/jib-jvm-flags-file) -cp $(cat /app/jib-classpath-file) $(cat /app/jib-main-class-file)
```

> **Requires Java 11+.** The Gradle variant supports `jib-gradle-plugin` 3.4.x / 3.5.x.

## Usage with Maven

Register the extension on your `jib-maven-plugin`:

```xml
<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>${jib-maven-plugin.version}</version>
  <configuration>
    <pluginExtensions>
      <pluginExtension>
        <implementation>tw.com.softleader.cloud.tools.jib.maven.JvmFlagsExtension</implementation>
      </pluginExtension>
    </pluginExtensions>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>tw.com.softleader.cloud.tools</groupId>
      <artifactId>jib-jvm-flags-extension-maven</artifactId>
      <version>${jib-jvm-flags-extension-maven.version}</version>
    </dependency>
  </dependencies>
</plugin>
```

Then pick one of the entrypoint patterns below.

### Entrypoint with a direct Java command

Use the JVM's `@filename` argument-file syntax to read flags inline:

```xml
<configuration>
  <container>
    <jvmFlags>
      <jvmFlag>-XshowSettings:vm</jvmFlag>
      <jvmFlag>-Xdebug</jvmFlag>
    </jvmFlags>
    <entrypoint>java,@/app/jib-jvm-flags-file,-cp,@/app/jib-classpath-file,@/app/jib-main-class-file</entrypoint>
  </container>
  <pluginExtensions>
    <pluginExtension>
      <implementation>tw.com.softleader.cloud.tools.jib.maven.JvmFlagsExtension</implementation>
    </pluginExtension>
  </pluginExtensions>
</configuration>
```

### Entrypoint with a shell script

Useful when you need env vars or setup steps before launching the JVM. Drop a script alongside your build:

```sh
#!/bin/bash
set -e

# Setup steps (env vars, certs, etc.)
export JAVA_TOOL_OPTIONS="-Xmx1g"

exec java $(cat /app/jib-jvm-flags-file) \
  -cp $(cat /app/jib-classpath-file) \
  $(cat /app/jib-main-class-file) \
  "$@"
```

Point the entrypoint at the script and ship it with the image:

```xml
<configuration>
  <container>
    <jvmFlags>
      <jvmFlag>-XshowSettings:vm</jvmFlag>
      <jvmFlag>-Xdebug</jvmFlag>
    </jvmFlags>
    <entrypoint>sh,/entrypoint.sh</entrypoint>
  </container>
  <extraDirectories>
    <paths>
      <path>
        <from>.</from>
        <includes>entrypoint.sh</includes>
      </path>
    </paths>
  </extraDirectories>
  <pluginExtensions>
    <pluginExtension>
      <implementation>tw.com.softleader.cloud.tools.jib.maven.JvmFlagsExtension</implementation>
    </pluginExtension>
  </pluginExtensions>
</configuration>
```

### Extension properties

| Property      | Default              | Description                                                  |
|---------------|----------------------|--------------------------------------------------------------|
| `skipIfEmpty` | `false`              | Skip the extension when no `jvmFlags` are configured         |
| `separator`   | `" "` (space)        | Character used to join flags inside the file                 |
| `filename`    | `jib-jvm-flags-file` | Output file name inside the container                        |
| `mode`        | `644`                | Output file permissions inside the container                 |

```xml
<pluginExtension>
  <implementation>tw.com.softleader.cloud.tools.jib.maven.JvmFlagsExtension</implementation>
  <properties>
    <skipIfEmpty>true</skipIfEmpty>
    <separator>,</separator>
    <filename>my-jvm-flags-file</filename>
    <mode>666</mode>
  </properties>
</pluginExtension>
```

## Usage with Gradle

Register the extension on your `jib-gradle-plugin`:

```gradle
// At the top of build.gradle
buildscript {
  dependencies {
    classpath 'tw.com.softleader.cloud.tools:jib-jvm-flags-extension-gradle:<VERSION>'
  }
}

jib {
  pluginExtensions {
    pluginExtension {
      implementation = 'tw.com.softleader.cloud.tools.jib.gradle.JvmFlagsExtension'
    }
  }
}
```

### Entrypoint with a direct Java command

```gradle
jib {
  container {
    jvmFlags = ['-XshowSettings:vm', '-Xdebug']
    entrypoint = ['java', '@/app/jib-jvm-flags-file', '-cp', '@/app/jib-classpath-file', '@/app/jib-main-class-file']
  }
  pluginExtensions {
    pluginExtension {
      implementation = 'tw.com.softleader.cloud.tools.jib.gradle.JvmFlagsExtension'
    }
  }
}
```

### Entrypoint with a shell script

Reuse the [shell script shown above](#entrypoint-with-a-shell-script), then wire it up in Gradle:

```gradle
jib {
  container {
    jvmFlags = ['-XshowSettings:vm', '-Xdebug']
    entrypoint = ['sh', '/entrypoint.sh']
  }
  extraDirectories {
    paths {
      path {
        from = '.'
        includes = ['entrypoint.sh']
      }
    }
  }
  pluginExtensions {
    pluginExtension {
      implementation = 'tw.com.softleader.cloud.tools.jib.gradle.JvmFlagsExtension'
    }
  }
}
```

### Extension properties

The properties are the same as documented [above](#extension-properties):

```gradle
pluginExtension {
  implementation = 'tw.com.softleader.cloud.tools.jib.gradle.JvmFlagsExtension'
  properties = [
    skipIfEmpty: 'true',
    separator: ',',
    filename: 'my-jvm-flags-file',
    mode: '666'
  ]
}
```

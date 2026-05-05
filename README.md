[![version](https://img.shields.io/github/v/release/softleader/jib-jvm-flags-extension-maven?color=brightgreen&sort=semver)](https://github.com/softleader/jib-jvm-flags-extension-maven/releases/latest)
[![Maven Central](https://img.shields.io/maven-central/v/tw.com.softleader.cloud.tools/jib-jvm-flags-extension-maven?color=orange)](https://central.sonatype.com/search?q=g%3Atw.com.softleader.cloud.tools&smo=true&namespace=tw.com.softleader.cloud.tools)
![GitHub tag checks state](https://img.shields.io/github/checks-status/softleader/jib-jvm-flags-extension-maven/main)
![GitHub issues](https://img.shields.io/github/issues-raw/softleader/jib-jvm-flags-extension-maven)

# Jib JVM Flags Extension

A [Jib](https://github.com/GoogleContainerTools/jib) [maven extension](https://github.com/GoogleContainerTools/jib-extensions) outputs the configured `jvmFlags` into the `/app/jib-jvm-flags-file` file, allowing a [custom entrypoint](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#custom-container-entrypoint) to access these flags.

When a custom entrypoint is used, Jib ignores the `jvmFlags` settings. This extension ensures that the configured `jvmFlags` are accessible even in such scenarios:

- For Java 11+:
  ```sh
  java @/app/jib-jvm-flags-file -cp @/app/jib-classpath-file @/app/jib-main-class-file
  ```
- With shell:
  ```sh
  java $(cat /app/jib-jvm-flags-file) -cp $(cat /app/jib-classpath-file) $(cat /app/jib-main-class-file)
  ```

> **Note:** Requires Java 11 or newer

## Usage

To use the Jib JVM Flags extension in your project, configure the `jib-maven-plugin` as follows:

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

### Customizing Entrypoint with Java Command

To customize the entrypoint using a direct Java command, for example:

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

### Customizing Entrypoint Using a Shell Script

You can also use a shell script to launch your app:

```sh
#!/bin/bash
set -e

# Perform any necessary steps before starting the JVM,
# such as setting JVM options or preparing the environment
export JAVA_TOOL_OPTIONS="-Xmx1g"

exec java $(cat /app/jib-jvm-flags-file) \
  -cp $(cat /app/jib-classpath-file) \
  $(cat /app/jib-main-class-file) \
  "$@"
```

And then configure the plugin to use this script:

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

### Extension Properties 

You can further customize the extension with the following properties:

```xml
<pluginExtension>
  <implementation>tw.com.softleader.cloud.tools.jib.maven.JvmFlagsExtension</implementation>
  <properties>
    <!-- Skip if no jvmFlags specified, Default: false -->
    <skipIfEmpty>true</skipIfEmpty>
    <!-- The separator character to use to join jvmFlags, Default: " " (space) --> 
    <separator>,</separator>
    <!-- Set the output file name in container, Default: jib-jvm-flags-file --> 
    <filename>my-jvm-flags-file</filename>
    <!-- Set the output file permissions in container, Default: 644 --> 
    <mode>666</mode>
  </properties>
</pluginExtension>
```

## Usage with Gradle

To use the Jib JVM Flags extension in your project, configure `jib-gradle-plugin` as follows:

```gradle
// should be at the top of build.gradle
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

> **Note:** Compatible with `jib-gradle-plugin` 3.4.x / 3.5.x.

### Customizing Entrypoint with Java Command (Gradle)

To customize the entrypoint using a direct Java command, for example:

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

### Customizing Entrypoint Using a Shell Script (Gradle)

Reuse the shell script shown in the Maven example above, then configure the plugin to invoke it:

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

### Extension Properties (Gradle)

You can further customize the extension with the following properties:

```gradle
pluginExtension {
  implementation = 'tw.com.softleader.cloud.tools.jib.gradle.JvmFlagsExtension'
  properties = [
    // Skip if no jvmFlags specified, Default: false
    skipIfEmpty: 'true',
    // The separator character to use to join jvmFlags, Default: " " (space)
    separator: ',',
    // Set the output file name in container, Default: jib-jvm-flags-file
    filename: 'my-jvm-flags-file',
    // Set the output file permissions in container, Default: 644
    mode: '666'
  ]
}
```

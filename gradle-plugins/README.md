# Publishing GraphDSL Plugins

From this directory:

```shell
./gradlew publishPlugins --no-configuration-cache
```

This will publish to gradle plugin portal.

~~Right now only snapshot versions are supported. Make sure your version in `plugins/build.gradle.kts` and `libs.versions.toml` ends with `-SNAPSHOT`.~~ GPP does not support snapshots. This is from when we were publishing plugins to maven central.

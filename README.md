# reproto-maven-plugin

This is a maven plugin intended to make it simple to integrate reproto into the lifecycle
of a [Maven] project.

You can run examples with the provided [`run-examples.sh`](run-examples.sh) script.

[Maven]: https://maven.apache.org/

## Usage

Create a [`reproto.toml` manifest](https://github.com/reproto/reproto/blob/master/doc/manifest.md)
in your project with the following settings:

```toml
language = "java"
presets = ["maven"]

[modules.jackson]
[modules.builder]

[packages]
"myapi" = "*"
```

Now enable this project in your `pom.xml`, since this is a Maven plugin, it is installed as an
extension like this:

```
<project>
  ...
  <build>
    <plugins>
      <plugin>
        <groupId>se.tedro.maven.plugins</groupId>
        <artifactId>reproto-maven-plugin</artifactId>
        <version>0.3.2</version>
        <extensions>true</extensions>
      </plugin>
      ...
    </plugins>
    ...
  </build>
  ...
</project>
```

Compiling your project will now also build your `.reproto` specifications under `src/main/reproto`.

```
$> mvn compile
```

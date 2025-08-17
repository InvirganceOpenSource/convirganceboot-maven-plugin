# Convirgance (Boot) Maven Plugin

This plugin performs the conversion of a Convirgance (Web Services) WAR file into an executable JAR file. 

## Usage

The plugin can be added to your Maven project or run from the command line.

# Add to POM

Add the following plugin to the `<build>` section of your `pom.xml` file:

```xml
<plugin>
    <groupId>com.invirgance</groupId>
    <artifactId>convirganceboot-maven-plugin</artifactId>
    <version>0.1.0</version>
    <executions>
        <execution>
            <goals>
                <goal>boot</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

# Command Line

Make sure you are in the directory of your web application project, then run the following command:

```
mvn com.invirgance:convirganceboot-maven-plugin:boot
```

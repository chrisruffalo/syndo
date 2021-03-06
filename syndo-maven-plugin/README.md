# Syndo (Συνδώ) Maven Plugin
Syndo is a Greek word meaning interconnect or link. It is meant to provide an OpenShift-native way to link
your artifacts to a custom build process without requiring a heavy investment in setup or a large amount
of administrative overhead.

The Maven plugin allows maven builds to leverage your Syndo workflow.

## Usage
```xml
<plugin>
    <groupId>io.github.chrisruffalo</groupId>
    <artifactId>syndo-maven-plugin</artifactId>
    <version>0.3.4</version>
    <executions>
        <execution>
            <!-- bind to the install phase -->
            <phase>install</phase>
            <goals>
                <!-- this is the same as the `syndo build` command line option -->
                <goal>build</goal>
            </goals>
            <configuration>
                <!-- required: path to the build file that will be used for the build -->
                <buildFile>${project.directory}/build.yml</buildFile>
                <!-- optional: namespace, the OpenShift namespace that is the target of the build -->
                <!--           when not provided uses the kube config's last used namespace -->
                <!--           can be provided by the syndo.namespace system property -->
                <namespace>syndo-target</namespace>
                <!-- optional: force build, default false, syndo.force system property if not provided -->
                <force>false</force>
                <!-- optional: force bootstrap, syndo.bootstrap.force system property if not provided -->
                <!--           can be provided by syndo.bootstrap.dir system property -->
                <forceBootstrap>false</forceBootstrap>
                <!-- optional: bootstrap content directory, where the content is for building the syndo builder container -->
                <!--           can be part of the build resources so that filtering can affect the directory -->
                <bootstrapDir>${project.build.directory}/custom-bootstrap</bootstrapDir>
                <!-- optional: list of components/aliases to build, default is all, should be comma-separated.  -->
                <!--           uses the syndo.components system property  -->
                <components>all</components>
                <!-- optional: if set to true then skip ssl verification. false is the default. -->
                <!--           uses the syndo.ssl.skip-verification system property -->
                <skipSslVerification>false</skipSslVerification>
            </configuration>
        </execution>
    </executions>
</plugin>
```

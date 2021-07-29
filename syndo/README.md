# Syndo (Συνδώ)
Syndo is a Greek word meaning interconnect or link. It is meant to provide an OpenShift-native way to link
your artifacts to a custom build process without requiring a heavy investment in setup or a large amount
of administrative overhead.

Syndo also aims to be efficient meaning that it builds more than one image in a single session which reduces
the need for pulling images to the build pod every time a new OpenShift build is started.

Syndo is also a tool that allows buildah to be used from any system that can talk to OpenShift: Windows, MacOS, or 
Linux clients can all use Syndo to create chained buildah builds.

Think of Syndo a little like a simple version of compose for OpenShift. This is a container build orchestration
tool.

## Features
* Creates custom build image and other OpenShift resources (ImageStreams) as needed
* Supports serialized buildah or Dockerfile (buildah bud) builds
* Resolves FROM/required builds at each step
* Resolves component build order and builds dependent components after their dependencies
* Works within a single namespace to produce output images
* Minimize pushes and pulls especially with interdependent image chains
* Allows for selectively building components and resolves component dependencies
* Can resolve images in the cluster (in accessible namespaces) or external (upstream registries)
* Images can select their own cache ("vfs" or "overlay") and RPM builds work in UBI images
* Can inject cache storage into Syndo build pods for caching image layers and artifacts between builds (see: [CACHE.md](CACHE.md))
* Configurable skipping of pushing intermediary builds to the registry

## Future Development
* Build parallelization (automatic / customizable)
* More condensed feedback of builds in the console

## Requirements
* JDK 8 (for the client)
* OpenShift 4.7+
* CustomBuildStrategy enabled in OpenShift for the user executing Syndo

## Future Work
* Pull secrets for upstream repositories
* Better work on syndo-runner tags/caching
* Better output instead of raw build logs (and better log handling / saving of logs)

## Why Should I Use Syndo?
You should use Syndo when you have a chain of images or when you need to otherwise orchestrate image building. It is ideal for situations
where you may have one or more intermediary layers when building applications. For example: you may have an image that creates and configures
an application server with common configuration and dependencies or certificates for your microservice. In these cases chaining dependent
builds on those layers all in the same buildah container produces faster builds that are also more consistent.

## Installing Syndo
Installing Syndo requires that you [enable custom build configurations](https://docs.openshift.com/container-platform/4.7/cicd/builds/securing-builds-by-strategy.html#securing-builds-by-strategy) 
in your cluster. Syndo will create all the artifacts it needs in the target namespace.

All the cluster resources required to build your artifacts will be published and managed by the Syndo binary.

## Syndo Build Process
The Syndo build proceeds in three phases:
1. Analysis - analyzes the configuration file is and creates the build plan. This determines the order that components will be built in.
2. Collection - collects artifacts into a single tar file for upload as part of the custom build
3. Execution - executes the build process in the order determined by the build plan, and pushes images to the registry

## Using Syndo
To use Syndo requires a build yaml file. The build yaml lays out the location of the artifacts that will be used
to construct the output container image. An [example file](sample-build/build.yml) is included with
comments to give some idea how a build might proceed.

To build with syndo:
```bash
[]$ java -jar syndo.jar build sample-build/build.yml
```

As each component is built the image source/from name and contents of the build directory will be used to make a sha256
hash that identifies the output image. This allows the build to skip subsequent builds for that component as long as the
contents (or the source image) are the same. In order to force a build use the `--force` option.
```bash
[]$ java -jar syndo.jar build sample-build/build.yml --force
```

Syndo supports building only specific components. During a typical run the implicit components being run are "all" but
the `--components` (`-c`) option allows a comma-separated list of components to be specified. All the components will
be resolved and any dependent components will also be added. If you have a build chain like "base" -> "custom" -> "application"
and execute with `-c application` Syndo will ensure that the "base" and "custom" dependencies are satisfied.
```bash
[]$ java -jar syndo.jar build sample-build/build.yml -c application
```

When Syndo creates its custom build image it will create a BuildConfig, Build, and ImageStream in the namespace that is targeted by Syndo. 
Subsequent builds will reuse the same build image.  For more information about what is in the build image and how to customize it
see the section `Customizing Syndo`.

## Customizing Syndo
Syndo can be customized to do pretty much anything you need. It is based on the `quay.io/containers/buildah` image to ensure that
the most up-to-date buildah features are available. You can build from any image you choose as long as it has buildah configured
properly for container-in-container builds and can correctly run the `syndo.sh` script.

To customize Syndo start by exporting the content:
```bash
[]$ java -jar syndo.jar export syndo-build-root
```

The contents of the `syndo-build-root` directory are the artifacts that are used for the Syndo build. You can modify these artifacts
at any time and use it as the root for your syndo build.
```bash
[]$ java -jar syndo.jar build sample-build/build.yml --bootstrap-path syndo-build-root 
```

If you just want to build the syndo image:
```bash
[]$ java -jar syndo.jar bootstrap --bootstrap-path syndo-build-root
```

Keep in mind that the results of bootstrap builds are also cached and to may need to force a bootstrap:
```bash
[]$ java -jar syndo.jar bootstrap --bootstrap-path syndo-build-root --force-bootstrap
```


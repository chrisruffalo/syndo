# Syndo (Συνδώ)
Syndo is a Greek word meaning interconnect or link. It is meant to provide an OpenShift-native way to link
your artifacts to a custom build process without requiring a heavy investment in setup or a large amount
of administrative overhead.

Syndo also aims to be efficient meaning that it builds more than one image in a single session which reduces
the need for pulling images to the build pod every time a new OpenShift build is started.

## Features
* Creates custom build image and other OpenShift resources (ImageStreams) as needed
* Supports serialized buildah or Dockerfile (buildah bud) builds
* FROM/required builds are resolved at each step
* Resolves component build order and builds dependent components after their dependencies
* Works within a single namespace to produce output images
* Minimize pushes and pulls
* Allows for selectively building components and resolves component dependencies

## Requirements
* JDK 8 (for the client)
* OpenShift 4.7+
* CustomBuildStrategy enabled in OpenShift for the user executing Syndo

## Future Work
* Resolution of upstream repositories (doesn't quite work right yet with openshift/resolution logic)
* Better work on syndo-builder tags/caching
* Guidance on image building on OpenShift (especially with dnf/subscriptions/entitlements being passed to container-in-container)

## Installing Syndo
Installing Syndo requires that you [enable custom build configurations](https://docs.openshift.com/container-platform/4.7/cicd/builds/securing-builds-by-strategy.html#securing-builds-by-strategy) 
in your cluster. Syndo will create all the artifacts it needs in the target namespace.

All the cluster resources required to build your artifacts will be published and managed by the Syndo binary.

## Syndo Build Process
The Syndo build proceeds in three phases:
1. Analysis - the configuration file is read and the build plan is created. This determines the order that components will be built in.
2. Collection - all the artifacts are collected into a single tar file for upload as part of the custom build
3. Execution - the build process is executed in the order determined by the build plan, images are pushed as they are built

## Using Syndo
To use Syndo requires a build yaml file. The build yaml lays out the location of the artifacts that will be used
to construct the output container image. An [example file](./sample-build/build.yml) is included with
comments to give some idea how a build might proceed.

To build with syndo:
```bash
[]$ java -jar syndo.jar build sample-build/build.yml
```

As each component is built the image source/from name and contents of the build directory will be used to make a sha256
hash that identifies the output image. This allows the build to skip subsequent builds for that component as long as the
contents (or the source image) are the same. In order to force a build use the `--force` option.
```bash
[]$ java -jar syndo.jar build sample-build/build.yml
```

When Syndo creates its custom build image it will create a BuildConfig, Build, and ImageStream in the namespace that is targeted by Syndo. 
Subsequent builds will reuse the same build image.  For more information about what is in the build image and how to customize it
see the section `Customizing Syndo`.

## Customizing Syndo
Syndo can be customized to do pretty much anything you need. It is based on the `quay.io/containers/buildah` image to ensure that
the most up-to-date buildah features are available. 

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


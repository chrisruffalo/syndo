# Syndo (Συνδώ)
Syndo is a Greek word meaning interconnect or link. It is meant to provide an OpenShift-native way to link
your artifacts to a custom build process without requiring a heavy investment in setup or a large amount
of administrative overhead.

Syndo also aims to be efficient meaning that it builds more than one image in a single session which reduces
the need for pulling images to the build pod every time a new OpenShift build is started.

## Compatibility
* OpenShift 4.7+
* JDK 8 (for the client)

## Installing Syndo
Installing Syndo requires that you [enable custom build processes](https://docs.openshift.com/container-platform/4.7/cicd/builds/securing-builds-by-strategy.html#securing-builds-by-strategy) 
in your cluster. Your cluster also needs to have access to the Syndo build image which can be built and pushed into 
the cluster from the `containers` directory or from an upstream registry.

All the cluster resources required to build your artifacts will be published and managed by the Syndo binary.

## Syndo Build Process
The Syndo build proceeds in three phases:
1. Analysis - the configuration file is read and the build plan is created. This determines the order that components will be built in.
2. Collection - all of the artifacts are collected into a single tar file for upload as part of the custom build
3. Execution - the build process is executed in the order determined by the build plan, images are pushed as they are built

## Using Syndo
To use Syndo requires a build yaml file. The build yaml lays out the location of the artifacts that will be used
to construct the output container image. An [example file](./src/test/resources/sample-build.yml) is included with
comments to give some idea how a build might proceed.
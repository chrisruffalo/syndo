# Syndo (Συνδώ)
Syndo is a Greek word meaning interconnect or link. It is meant to provide an OpenShift-native way to link
your artifacts to a custom build process without requiring a heavy investment in setup or a large amount
of administrative overhead.

## Background
Syndo came from a project with a lot of container images where spawning a series of Docker builds was imposing heavy overhead
on the cluster. The same layers, especially intermediary layers, would be pulled over and over again as build pods might not
be scheduled on the same node as the previous step.

Chaining buildah builds in a single OpenShift build which would allow the intermediate images to stay within the build 
context and be reused. On top of buildah being faster in general this reduced our container build time from over an hour down 
to about fifteen minutes.

Syndo also fills a need for developers who want to quickly get pods into OpenShift because it handles a lot of the logistics
such as build and buildconfig resources. It is also multi-platform and works as a stand-alone binary, an executable jar from
maven, or a maven plugin. Being able to orchestrate buildah builds from any platform saves time and frustration.

## Components
* [Syndo](./syndo/README.md) - The jar executables for the syndo command line application. Allows Syndo to be used outside of maven.
* [Syndo Maven Plugin](./syndo-maven-plugin/README.md) - Maven plugin that allows execution of Syndo within a maven build
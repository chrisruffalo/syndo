#!/bin/bash

# This is the common build file that knows to use yum/dnf and what packages to install and/or repositories
# to add/initialize depending on how it is going to install the necessary base packages. it is always intended
# to be the common platform for RHEL/UBI/Cent installs that makes it so that syndo.sh runs the same way
# regardless of the platform.

# enable container-tools repo
#dnf -y module enable container-tools:rhel8

# install additional packages
dnf install -y zip unzip file #skopeo fuse-overlays

# clean up
rm -rf /var/cache
rm -rf /var/log/dnf*
rm -rf /var/log/yum.*

# begin setup for podman/buildah on docker, all of these commands are more or less to enable the use of the
# sub uid/gid tools for buildah to use
chmod g=u /etc/passwd
mkdir -p /run/{lock,containers}
chown -R 1001:0 /run/{lock,containers}
chmod g=u /etc/containers
touch /etc/subuid
touch /etc/subgid
chmod g=u /etc/sub*
chmod 4755 /usr/bin/new{u,g}idmap

# ensure the root syndo directory is created
mkdir -p /syndo
chown -R 1001:0 /syndo
chmod g=u /syndo
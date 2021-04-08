#!/bin/bash

# This is the common build file that knows to use yum/dnf and what packages to install and/or repositories
# to add/initialize depending on how it is going to install the necessary base packages. it is always intended
# to be the common platform for RHEL/UBI/Cent installs that makes it so that syndo.sh runs the same way
# regardless of the platform.

# here is a list of all the packages that need to be installed no matter the platform
PACKAGES="sudo zip unzip file slirp4netns"

# determine if we are using YUM or DNF
PM="$(which dnf)"
if [[ 0 != $? ]]; then
  PM="$(which yum)"
fi

# todo: determine if we need to add repositories
# this should be enabling the RHSCL and the server extras based on the release and version
ENABLE_REPOS=""

# install additional packages
${PM} update -y
${PM} ${ENABLE_REPOS} install -y ${PACKAGES}

# clean up
${PM} clean all
rm -rf /var/cache/yum
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
#!/bin/bash

# install additional packages if needed
#dnf install -y zip unzip file

# clean up
rm -rf /var/cache
rm -rf /var/log/dnf*
rm -rf /var/log/yum.*

# ensure the root syndo directory is created and is available
mkdir -p /syndo
chown -R 1001:0 /syndo
chmod -R g=u /syndo
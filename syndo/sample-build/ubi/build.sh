#!/bin/bash

# switch to user 0 to install rpms
buildah config --user 0 ${CONTAINER}

# install rpms (this should use subscriptions/entitlements from underlying cluster)
buildah run ${CONTAINER} -- dnf install -y bind-utils

# return to regular user after build complete
buildah config --user 1001 ${CONTAINER}
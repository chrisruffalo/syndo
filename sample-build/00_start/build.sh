#!/bin/bash

cp start.sh ${MOUNTPOINT}/start.sh
chmod +x ${MOUNTPOINT}/start.sh

buildah run ${CONTAINER} -- dnf install -y curl

buildah config --entrypoint /start.sh ${CONTAINER}
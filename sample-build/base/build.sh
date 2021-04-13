#!/bin/bash

cp start.sh ${MOUNTPOINT}/start.sh
chmod +x ${MOUNTPOINT}/start.sh

buildah config --entrypoint /start.sh ${CONTAINER}
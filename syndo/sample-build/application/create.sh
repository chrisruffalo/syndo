#!/bin/bash

cp banner.sh ${MOUNTPOINT}/banner.sh

buildah config --entrypoint /start.sh ${CONTAINER}
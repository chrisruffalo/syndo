#!/bin/bash

# Syndo.sh is the entrypoint for the container build system. It's entire job is to do the following tasks
# * Prepare the buildah environment for each build task in the build package
# * Execute the build tasks required in each of the tasks uploaded as part of the build package
# * Commit / send the output of the build task to the right repository tag

# this script is heavily modified but descends from https://docs.openshift.com/container-platform/4.7/cicd/builds/custom-builds-buildah.html

# set the environment variables for rootless BUILDAH, this isn't done elsewhere because it would add to the complexity
# of the image and is really only needed at the tool's runtime. setting them here puts them squarely in the responsibility
# of this script
export BUILDAH_ISOLATION=chroot
export _BUILDAH_STARTED_IN_USERNS=1
export STORAGE_DRIVER=vfs
export BUILD_STORAGE_DRIVER=${STORAGE_DRIVER}

# create buildah alias
alias buildah=$(which buildah)

# update the uid/gid settings for the subuid/subgid mapping
STARTING_ID="1000000000" # the id that will be the minimum user id for subuid
ID_COUNT=$(cat /proc/sys/user/max_user_namespaces)
echo "${USER}:${STARTING_ID}:${ID_COUNT}" >> /etc/subuid
echo "${UID}:${STARTING_ID}:${ID_COUNT}" >> /etc/subuid
echo "${USER}:${STARTING_ID}:${ID_COUNT}" >> /etc/subgid
echo "$(id -g):${STARTING_ID}:${ID_COUNT}" >> /etc/subgid

# create the working directory for syndo
mkdir -p /syndo/working

# this is the magic that takes the stdin that gets uploaded to
# the build configuration and puts it where it can be worked on
echo "Waiting for uploaded tar..."
tar xz -C /syndo/working

# import the openshift pull secret available to the current service account, these are kept as separate variables
# here because there is significant chance that later on we will be adding the ability for these to use different
# sources and they would need different secrets

# need to use the built-in push secret to push to the internal registry
cp /var/run/secrets/openshift.io/push/.dockercfg /tmp
(echo "{ \"auths\": " ; cat /var/run/secrets/openshift.io/push/.dockercfg ; echo "}") > /tmp/.dockercfg

# create directory list and step through them
DIRECTORIES=/syndo/working/*/
for dir in ${DIRECTORIES[@]}; do

  # this normalizes the path so that redundant slashes are removed
  dir=$(realpath "${dir}")

  # make sure at least a metadata directory exists or skip
  if [[ ! -f ${dir}/.meta/env ]]; then
    echo "No metadata found for ${dir}, skipping"
    continue
  fi

  # the metadata env file is provided by the syndo java process for each component uploaded that needs to be built
  # and it needs to export the following environment variables
  #   COMPONENT - the name of the current component being built
  #   FROM_IMAGE - the resolved image reference that will be fed to the buildah command
  #   OUTPUT_TARGET - the target ref to push the image to
  # optional environment variables:
  #   KEEP - "true" if the image should be kept (set to true when the image is the source for another image)
  source "${dir}/.meta/env"

  # make sure a build.sh build script exists in the directory
  if [[ ! -f ${dir}/build.sh ]]; then
    echo "Could not build in ${dir}, no build.sh script exists"
    exit 1
  fi

  # use the component name as the output image name if no output
  # image name is given
  if [[ "x" == "x${OUTPUT_NAMESPACE}" ]]; then
    OUTPUT_NAMESPACE=${OPENSHIFT_BUILD_NAMESPACE}
  fi
  if [[ "x" == "x${OUTPUT_TARGET}" ]]; then
    OUTPUT_TARGET=${OUTPUT_NAMESPACE}/${COMPONENT}
  fi

  # set a temporary directory for use by buildah
  TMPDIR="${dir}/.buildahtmp"
  mkdir -p ${TMPDIR}
  export TMPDIR

  # change build context to directory
  cd ${dir}
  echo "Building '${COMPONENT}' from '${FROM_IMAGE}' in ${dir}"

  # record the start time
  START_TIME="$(date -u +%s)"

  # now actually start the container / pull / open the container context with buildah
  CONTAINER=$(buildah from --authfile=/tmp/.dockercfg --tls-verify=false ${FROM_IMAGE})
  if [[ "x" == "x${CONTAINER}" ]]; then
    echo "No container pulled for ${FROM_IMAGE}"
    exit 1
  fi
  export CONTAINER
  echo "Using container ${CONTAINER}"

  # export some helpful variables for use inside the build.sh uploaded as the build step
  export MOUNTPOINT=$(buildah mount ${CONTAINER})

  # now actually run the build script
  (set -x && $(which bash) ${dir}/build.sh)
  EXIT_CODE=$?
  if [[ 0 == ${EXIT_CODE} ]]; then
    # if build information is provided in the metadata move it to /build/info inside the container
    if [[ -f "${dir}/.meta/buildinfo" ]]; then
      mkdir -p ${MOUNTPOINT}/build/info
      cp "${dir}/.meta/buildinfo" ${MOUNTPOINT}/build/info
    fi

    # commit the container and then remove the container
    buildah commit ${CONTAINER} ${OUTPUT_TARGET}
    buildah rm ${CONTAINER}

    # push the output image
    echo "Pushing ${OUTPUT_TARGET} -> ${OUTPUT_REGISTRY}/${OUTPUT_TARGET}"
    buildah push --authfile=/tmp/.dockercfg --tls-verify=false ${OUTPUT_TARGET} ${OUTPUT_REGISTRY}/${OUTPUT_TARGET}

    # the keep file signals that we need to keep the image for dependent build steps, if this file does not exist
    # we delete the image so as to save space (we don't want image space to fill up)
    if [[ "xtrue" != "x${KEEP}" ]]; then
      buildah rmi -f ${OUTPUT_TARGET}
    fi

    # output stats time
    echo "${COMPONENT} finished in $(($(date -u +%s) - ${START_TIME}))s" >> /syndo/working/stats
  else
    # clean up and move on
    buildah rm ${CONTAINER}
    echo "${COMPONENT} failed after $(($(date -u +%s) - ${START_TIME}))s" >> /syndo/working/stats
  fi
done

# output all stats if it exists
if [[ -f /syndo/working/stats ]]; then
  echo "===================================="
  echo "Build Summary:"
  echo "===================================="
  cat /syndo/working/stats
  echo "===================================="
fi
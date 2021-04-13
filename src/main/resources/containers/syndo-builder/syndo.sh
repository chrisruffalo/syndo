#!/bin/bash

# Syndo.sh is the entrypoint for the container build system. It's entire job is to do the following tasks for each component:
# * Prepare the buildah environment for each component in the build package
# * Execute the build tasks required in each of the tasks uploaded as part of the build package
# * Commit / send the output of the build task to the right repository tag

# this script is heavily modified but descends from https://docs.openshift.com/container-platform/4.7/cicd/builds/custom-builds-buildah.html

# set the environment variables for rootless BUILDAH, this isn't done elsewhere because it would add to the complexity
# of the image and is really only needed at the tool's runtime. setting them here puts them squarely in the responsibility
# of this script
export BUILDAH_ISOLATION=${BUILDAH_ISOLATION:-chroot}
export STORAGE_DRIVER=${STORAGE_DRIVER:-vfs}
export BUILD_STORAGE_DRIVER=${STORAGE_DRIVER}

echo "-----------------------------------------"
echo " ENV ENV ENV ENV ENV ENV ENV ENV ENV ENV "
echo "-----------------------------------------"
env
echo ""
echo "-----------------------------------------"

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

# need to use the built-in push secret to push to the internal registry and they need to be slightly modified to work
# as according to https://docs.openshift.com/container-platform/4.7/cicd/builds/custom-builds-buildah.html
(echo "{ \"auths\": " ; cat ${PULL_DOCKERCFG_PATH}/.dockercfg ; echo "}") > /tmp/.dockercfg-pull
(echo "{ \"auths\": " ; cat ${PUSH_DOCKERCFG_PATH}/.dockercfg ; echo "}") > /tmp/.dockercfg-push

# create directory list and step through them
DIRECTORIES=/syndo/working/*/
for DIR in ${DIRECTORIES[@]}; do
  # this is the outer subshell that prevents environment variables from leaking out of the exeuction and causing issues
  # in other shells.
  (
    # this normalizes the path so that redundant slashes are removed
    DIR=$(realpath "${DIR}")
    export DIR

    # change build context to directory
    cd ${DIR}

    # make sure at least a metadata directory exists or skip
    if [[ ! -f ${DIR}/.meta/env ]]; then
      echo "No metadata found in ${DIR}, skipping"
      continue
    fi

    # the metadata env file is provided by the syndo java process for each component uploaded that needs to be built
    # and it needs to export the following environment variables
    #   COMPONENT - the name of the current component being built
    #   FROM_IMAGE - the resolved image reference that will be fed to the buildah command
    #   OUTPUT_TARGET - the target ref to push the image to
    # optional environment variables:
    #   DOCKERFILE - if using a dockerfile build then the dockerfile path from the ${DIR} root and it will use `buildah bud`
    #   KEEP - "true" if the image should be kept (set to true when the image is the source for another image)
    source "${DIR}/.meta/env"

    # get full/real path to dockerfile if dockerfile is given
    if [[ "x" != "x${DOCKERFILE}" ]]; then
      DOCKERFILE=$(realpath "${DIR}/${DOCKERFILE}")
    fi

    # make sure a build.sh build script exists in the directory
    if [[ "x" == "x${DOCKERFILE}" && ! -f ${DIR}/build.sh ]]; then
      echo "Could not build in ${DIR}, no build.sh exists"
      exit 1
    elif [[ "x" != "x${DOCKERFILE}" && ! -f "${DOCKERFILE}" ]]; then
      echo "Dockerfile ${DOCKERFILE} specified but does not exist"
      exit 1
    fi

    # use this as the default from registry if nothing is defined in the input
    FROM_REGISTRY=${FROM_REGISTRY:-image-registry.openshift-image-registry.svc:5000}

    # simple ref to local namespace
    NAMESPACE=${OPENSHIFT_BUILD_NAMESPACE}
    export NAMESPACE

    # use the component name as the output image name if no output
    # image name is given
    if [[ "x" == "x${OUTPUT_NAMESPACE}" ]]; then
      OUTPUT_NAMESPACE=${NAMESPACE}
    fi
    if [[ "x" == "x${OUTPUT_TARGET}" ]]; then
      OUTPUT_TARGET=${OUTPUT_NAMESPACE}/${COMPONENT}
    fi

    # set a temporary directory for use by buildah, not having this set can cause issues with  storage
    # space on rootless builds
    TMPDIR="${DIR}/.buildahtmp"
    mkdir -p ${TMPDIR}
    export TMPDIR

    echo "Building '${COMPONENT}' from '${FROM_IMAGE}' in ${DIR}"

    # record the start time
    START_TIME="$(date -u +%s)"

    EXIT_CODE=0
    if [[ "x" != "x${DOCKERFILE}" ]]; then
      (
        buildah --authfile=/tmp/.dockercfg-pull --storage-driver vfs bud --isolation chroot -t ${OUTPUT_TARGET} -f ${DOCKERFILE} ${DIR}
      )
      EXIT_CODE=$?
    else
      # now actually start the container / pull / open the container context with buildah
      CONTAINER=$(buildah --storage-driver=${STORAGE_DRIVER} from --authfile=/tmp/.dockercfg-pull --tls-verify=false ${FROM_REGISTRY}/${FROM_IMAGE})
      if [[ "x" == "x${CONTAINER}" || "x0" != "x$?" ]]; then
        echo "No container pulled for ${FROM_IMAGE}"
        exit 1
      fi
      export CONTAINER
      echo "Using container ${CONTAINER}"

      # export some helpful variables for use inside the build.sh uploaded as the build step
      export MOUNTPOINT=$(buildah mount ${CONTAINER})

      # now actually run the build script with command tracing and by bubbling out errors
      (
        bash -v -e ${DIR}/build.sh
      )
      EXIT_CODE=$?

      # commit the container to an image and remove the container
      buildah --storage-driver=${STORAGE_DRIVER} commit --rm ${CONTAINER} ${OUTPUT_TARGET}
    fi

    if [[ "x0" == "x${EXIT_CODE}" ]]; then
      # push the output image
      echo "Pushing ${OUTPUT_TARGET} -> ${OUTPUT_REGISTRY}/${OUTPUT_TARGET}"
      ls -lah /var/lib/shared/vfs-images
      buildah --storage-driver=${STORAGE_DRIVER} push --tls-verify=false --authfile=/tmp/.dockercfg-push ${OUTPUT_TARGET} ${OUTPUT_REGISTRY}/${OUTPUT_TARGET}

      if [[ "x" != "x${OUTPUT_TAG}" && "latest" != "${OUTPUT_TAG}" ]]; then
        skopeo copy --authfile=/tmp/.dockercfg-pull --dest-tls-verify=false --src-tls-verify=false docker://${OUTPUT_REGISTRY}/${OUTPUT_TARGET} docker://${OUTPUT_REGISTRY}/${OUTPUT_TARGET}:${OUTPUT_TAG}
      fi

      # the keep file signals that we need to keep the image for dependent build steps, if this file does not exist
      # we delete the image so as to save space (we don't want image space to fill up)
      if [[ "xtrue" != "x${KEEP}" ]]; then
        buildah --storage-driver=${STORAGE_DRIVER} rmi -f ${OUTPUT_TARGET}
      fi

      # output stats time
      echo "${COMPONENT} (${OUTPUT_TARGET}) finished in $(($(date -u +%s) - ${START_TIME}))s" >> /syndo/working/stats
    else
      echo "${COMPONENT} failed after $(($(date -u +%s) - ${START_TIME}))s" >> /syndo/working/stats
    fi
  )
done

# output all stats if it exists
if [[ -f /syndo/working/stats ]]; then
  echo "===================================="
  echo "Build Summary:"
  echo "===================================="
  cat /syndo/working/stats
  echo "===================================="
fi
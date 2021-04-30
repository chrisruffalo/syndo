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

# create the working directory for syndo
mkdir -p /syndo/working

# this is the magic that takes the stdin that gets uploaded to
# the build configuration and puts it where it can be worked on
echo "Waiting for uploaded tar..."
tar xz -C /syndo/working

PULL_AUTHFILE=/tmp/.authfile-pull
PUSH_AUTHFILE=/tmp/.authfile-push
# need to use the built-in push secret to push to the internal registry and they need to be slightly modified to work
# as according to https://docs.openshift.com/container-platform/4.7/cicd/builds/custom-builds-buildah.html.
(echo "{ \"auths\": " ; cat ${PULL_DOCKERCFG_PATH}/.dockercfg ; echo "}") > ${PULL_AUTHFILE}
(echo "{ \"auths\": " ; cat ${PUSH_DOCKERCFG_PATH}/.dockercfg ; echo "}") > ${PUSH_AUTHFILE}

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
    #   DOCKERFILE - if using a dockerfile build then this is the dockerfile path from the ${DIR} root and it will use `buildah bud`
    #   KEEP - "true" if the image should be kept (set to true when the image is the source for another image)
    #   RESOLVED - "true" if the image is internal to the cluster (resolved in the cluster) and needs the registry prefix to be downloaded
    #   BUILD_SCRIPT - the path to another build file to use, build.sh is the default
    #   STORAGE_DRIVER - the storage driver to use (overlay is default but some builds will only work with vfs)
    source "${DIR}/.meta/env"

    # set the storage driver based on what is given in the environment, defaulting to "overlay" because it is faster
    # in the absence of a reason to use the vfs driver (which is slower).
    export STORAGE_DRIVER=${STORAGE_DRIVER:-overlay}
    export BUILD_STORAGE_DRIVER=${STORAGE_DRIVER}

    # get full/real path to dockerfile if dockerfile is given
    if [[ "x" != "x${DOCKERFILE}" ]]; then
      DOCKERFILE=$(realpath "${DIR}/${DOCKERFILE}")
    fi

    # make sure a build.sh build script exists in the directory or there is a dockerfile
    BUILD_SCRIPT=${BUILD_SCRIPT:-build.sh}
    if [[ "x" == "x${DOCKERFILE}" && ! -f ${DIR}/${BUILD_SCRIPT} ]]; then
      echo "Could not build in ${DIR}, no ${BUILD_SCRIPT} exists"
      exit 1
    elif [[ "x" != "x${DOCKERFILE}" && ! -f "${DOCKERFILE}" ]]; then
      echo "Dockerfile ${DOCKERFILE} specified but does not exist"
      exit 1
    fi

    # use this as the default from registry if nothing is defined in the input and the
    # input image is marked as being resolved from the registry
    FROM_REGISTRY=""
    if [[ "xtrue" == "x${RESOLVED}" ]]; then
      # note the slash at the end of the FROM_REGISTRY
      FROM_REGISTRY=${FROM_REGISTRY:-image-registry.openshift-image-registry.svc:5000/}
    fi

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

    echo "Building '${COMPONENT}' from '${FROM_REGISTRY}${FROM_IMAGE}' in ${DIR}"

    # import more values into imports file
    echo "DIR=${DIR}" >> ${DIR}/.meta/imports
    echo "STORAGE_DRIVER=${STORAGE_DRIVER}" >> ${DIR}/.meta/imports
    echo "BUILD_STORAGE_DRIVER=${STORAGE_DRIVER}" >> ${DIR}/.meta/imports
    echo "BUILDAH_ISOLATION=${BUILDAH_ISOLATION}" >> ${DIR}/.meta/imports

    # record the start time
    START_TIME="$(date -u +%s)"

    EXIT_CODE=0
    if [[ "x" != "x${DOCKERFILE}" ]]; then
      (
        # using --from here allows us to skip messing with the FROM line of the docker file and allows us to get proper resolution of different types of artifacts the way that buildah does it (and not docker)
        buildah --authfile=${PULL_AUTHFILE} --tls-verify=false --isolation ${BUILDAH_ISOLATION} --storage-driver ${STORAGE_DRIVER} bud --from "${FROM_REGISTRY}${FROM_IMAGE}" -t ${OUTPUT_TARGET} -f ${DOCKERFILE} ${DIR}
      )
      EXIT_CODE=$?
    else
      # pull and create the container context with buildah
      CONTAINER=$(buildah --storage-driver=${STORAGE_DRIVER} --authfile=${PULL_AUTHFILE} --tls-verify=false from ${FROM_REGISTRY}${FROM_IMAGE})
      if [[ "x" == "x${CONTAINER}" || "x0" != "x$?" ]]; then
        echo "${COMPONENT} failed to pull ${FROM_REGISTRY}${FROM_IMAGE}" >> /syndo/working/stats
        exit 1
      fi
      export CONTAINER
      echo "CONTAINER=${CONTAINER}" >> ${DIR}/.meta/imports
      echo "Using container ${CONTAINER}"

      # export some helpful variables for use inside the build.sh uploaded as the build step
      MOUNTPOINT=$(buildah mount ${CONTAINER})
      echo "MOUNTPOINT=${MOUNTPOINT}" >> ${DIR}/.meta/imports

      # now actually run the build script with command tracing and by bubbling out errors
      (
        bash -v -e ${DIR}/${BUILD_SCRIPT}
      )

      EXIT_CODE=$?
      if [[ "x0" == "x${EXIT_CODE}" ]]; then
        # commit the container to an image and remove the container
        buildah --storage-driver=${STORAGE_DRIVER} commit --rm ${CONTAINER} ${OUTPUT_TARGET}
      fi
    fi

    if [[ "x0" == "x${EXIT_CODE}" ]]; then
      # push the output image to the target and then the tagged location(s) for this build
      echo "Pushing ${OUTPUT_TARGET} -> ${OUTPUT_REGISTRY}/${OUTPUT_TARGET}"
      buildah --storage-driver=${STORAGE_DRIVER} push --tls-verify=false --authfile=${PUSH_AUTHFILE} ${OUTPUT_TARGET} ${OUTPUT_REGISTRY}/${OUTPUT_TARGET}
      echo "Pushing ${OUTPUT_TARGET} -> ${OUTPUT_REGISTRY}/${OUTPUT_TARGET}:${OPENSHIFT_BUILD_NAME}"
      buildah --storage-driver=${STORAGE_DRIVER} push --tls-verify=false --authfile=${PUSH_AUTHFILE} ${OUTPUT_TARGET} ${OUTPUT_REGISTRY}/${OUTPUT_TARGET}:${OPENSHIFT_BUILD_NAME}

      if [[ "x" != "x${HASH}" ]]; then
        echo "Pushing ${OUTPUT_TARGET} -> ${OUTPUT_REGISTRY}/${OUTPUT_TARGET}:${HASH}"
        buildah --storage-driver=${STORAGE_DRIVER} push --tls-verify=false --authfile=${PUSH_AUTHFILE} ${OUTPUT_TARGET} ${OUTPUT_REGISTRY}/${OUTPUT_TARGET}:${HASH}
      fi

      if [[ "x" != "x${OUTPUT_TAG}" && "latest" != "${OUTPUT_TAG}" ]]; then
        echo "Pushing ${OUTPUT_TARGET} -> ${OUTPUT_REGISTRY}/${OUTPUT_TARGET}:${OUTPUT_TAG}"
        buildah --storage-driver=${STORAGE_DRIVER} push --tls-verify=false --authfile=${PUSH_AUTHFILE} ${OUTPUT_TARGET} ${OUTPUT_REGISTRY}/${OUTPUT_TARGET}:${OUTPUT_TAG}
      fi

      # the keep variable signals that we need to keep the image for dependent build steps, if this file does not exist
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
  echo "======================================================================"
  echo "Build Summary:"
  echo "======================================================================"
  cat /syndo/working/stats
  echo "======================================================================"
fi
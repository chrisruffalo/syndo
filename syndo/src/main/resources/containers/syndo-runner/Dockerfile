# use the quay buildah image for up-to-date features and configuration
FROM quay.io/containers/buildah:latest

# copy the command and then execute it, the point here is to build as few layers as possible
COPY *.sh /.
RUN chmod +x /build.sh \
 && chmod +x /syndo.sh \
 && /bin/bash /build.sh \
 && rm -rf /build.sh

# set the container entrypoint build script
ENTRYPOINT /syndo.sh


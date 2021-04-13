# use the rhel8-based buildah image which is built for this sort of task already
FROM registry.fedoraproject.org/fedora-minimal

# copy the command and then execute it, the point here is to build as few layers as possible
COPY *.sh /.
RUN chmod +x /build.sh \
 && chmod +x /syndo.sh \
 && /bin/bash /build.sh \
 && rm -rf /build.sh

ENV STORAGE_DRIVER=vfs _BUILDAH_STARTED_IN_USERNS="" BUILDAH_ISOLATION=chroot

# set the container entrypoint build script
ENTRYPOINT /syndo.sh


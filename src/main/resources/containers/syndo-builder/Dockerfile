# use the rhel8-based buildah image which is built for this sort of task already
FROM registry.redhat.io/rhel8/buildah

# copy the command and then execute it, the point here is to build as few layers as possible
COPY *.sh /.
RUN chmod +x /common.sh \
 && chmod +x /syndo.sh \
 && /bin/bash /common.sh \
 && rm -rf /common.sh

# set the container entrypoint build script
ENTRYPOINT /syndo.sh


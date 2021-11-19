# Build Cache Augmentation

## Installing Build Cache Augmentation
To install storage into your cluster simply run the following command:
```bash
[]$ java -jar syndo.jar storage 
```
This command will check the permissions of the current user and install the required resources into the cluster. It will 
add a service and pod to respond to webhook requests as well as a mutating webhook cluster resource.
```bash
Usage: cache [options]
Options:
  --build-log-type, -L
    The output type to use from the build logs. Valid values are
    'graphic', 'short', 'full', and 'none'. The value 'graphic' will
    output progress bars, 'short' will only output certain messages,
    'full' outputs all build logs, and 'none' suppresses all build
    output. This value is not case sensitive. Invalid values result in
    'full'.
    Default: short
  --force-cache, -F
    Setting this option to true forces the build of the Syndo cache
    webhook container even if the proper version is already present
    Default: false
  --namespace, -n
    Target OpenShift namespace that will be used for the resolution of
    build artifacts and as the ouput target for build items, defaults
    to the current namespace in use by the oc client
    Default: syndo-infra
  --openshift-config-path, -O
    Path to the directory where the kube authentication/configuration
    file can be found
    Default: [/home/cruffalo/.kube]
  --ssl-skip-verify
    If true then skip SSL verification
    Default: false
  --cache-resource-path, -P
    Path to the artifacts to use for the Syndo cache webhook
    container image
```

| Name | Option | Description |
| ---- | --------|-------------|
| Force Cache Install | --force-cache | Forces the install of Cache resources even if Syndo thinks that the storage Cache infrastructure has been installed.
| Cache Resource Path | --cache-resource-path | Path to the directory containing the Dockerfile and resources to build the Build Cache Augmentation Service
| Namespace | --namespace | The namespace that the Cache Augmentation Service will be installed in

## Enabling Build Cache Augmentation
In order to enable cache augmentation on your Syndo builds you need to enable the cache in the build config:
```yaml
  # syndo can inject storage into select build pods so that they can use/reuse data from previous builds like
  # the image layers. this allows builds to be faster but has some caveats and some special behaviors. for more
  # information see README.md and CACHE.md
  cache:
    # enables a build cache, set to false to disable adding a storage claim to the pod
    enabled: true
    # set this to a value to use that storage class when creating a claim
    #storage-class: fast
    # the name of the storage claim to use
    #claim-name: syndo-cache
    # setting this to true (default) makes the PVC mode "ReadWriteMany"
    shared: true
    # the size will be used for the resource storage request on the created persistent volume claim
    size: "10Gi"
```
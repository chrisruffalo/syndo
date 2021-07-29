// initially from https://github.com/soukron/openshift-admission-webhooks/blob/master/enforceenv/src/handler.js

// libraries
const express    = require('express'),
      fs         = require('fs'),
      request    = require('request');
      base64     = require('js-base64').Base64;

// annotations
const ANNOTATION_CACHE_ENABLED = 'syndo/cache-enabled';

// api url, CA and authorization token
const apiCA    = fs.readFileSync('/var/run/secrets/kubernetes.io/serviceaccount/ca.crt', 'utf8'),
      apiUrl   = `https://${process.env.KUBERNETES_SERVICE_HOST}:${process.env.KUBERNETES_SERVICE_PORT}`,
      apiToken = fs.readFileSync('/var/run/secrets/kubernetes.io/serviceaccount/token', 'utf8');

// router instance
let router = express.Router();

function handleCallback(err, res, data, callback) {
    if (err || res.statusCode !== 200) {
        if (err == null) {
            if (res.body != null && res.body.message != null) {
                err = res.body.message
            } else {
                err = JSON.stringify(res.body);
            }
        }
        callback(`Error ${res.statusCode} when retrieving data: ${err}`, null);
    } else {
        callback(null, data);
    }
}

// get resource from API
function getApiResource(api, namespace, kind, name, callback) {
    request.get({
        ca: apiCA,
        url: `${apiUrl}/apis/${api}/v1/namespaces/${namespace}/${kind}/${name}`,
        json: true,
        headers: {'Authorization': `Bearer ${apiToken}` }
    }, (err, res, data) => {
        handleCallback(err, res, data, callback);
    });
}

// get vanilla k8s api
function getK8sResource(namespace, kind, name, callback) {
    request.get({
        ca: apiCA,
        url: `${apiUrl}/api/v1/namespaces/${namespace}/${kind}/${name}`,
        json: true,
        headers: {'Authorization': `Bearer ${apiToken}` }
    }, (err, res, data) => {
        handleCallback(err, res, data, callback);
    });
}

function respondOk(res, admissionResponse) {
    // admit the item (regardless)
    res.send(JSON.stringify(admissionResponse));

    // log
    //console.log(admissionResponse, null, 2);

    // return 200 and end
    res.status(200).end();
}

// process POST
router.post('/build-cache-mutator', (req, res) => {
    // set the proper header for the response
    res.setHeader('Content-Type', 'application/json');

    // create a response
    let admissionResponse = {
        response: {
            uid: req.body.request.uid,
            allowed: true, // by default allow no matter what
        }
    };

    // get object
    let object = req.body.request.object;

    // log object
    //console.log(JSON.stringify(object, null, 2));

    // ensure the kind is pod
    if (object != null && object.kind != null && object.kind === 'Pod') {
        // basic pod metadata
        let namespace = object.metadata.namespace;
        let podName = object.metadata.name;

        // check adn see if it is already configured
        if (object.metadata.annotations != null && object.metadata.annotations[ANNOTATION_CACHE_ENABLED] != null && object.metadata.annotations[ANNOTATION_CACHE_ENABLED] === true) {
            console.log(`${podName} is already configured for caching`);
            respondOk(res, admissionResponse);
            return;
        }

        // get the build name so that we can look up the buildconfig and
        // see if it belongs to syndo
        let buildName = object.metadata.labels['openshift.io/build.name'];
        getApiResource("build.openshift.io", namespace,"builds", buildName, function (err, buildData) {
            if (err != null) {
                respondOk(res, admissionResponse);
                console.error(err)
                return;
            }

            // ensure we got a build with a linked buildconfiguration, exit otherwise
            if (buildData != null && buildData.kind != null && buildData.kind !== 'Build' && buildData.metadata != null && buildData.metadata.labels != null && buildData.metadata.labels['openshift.io/build-config.name'] != null) {
                respondOk(res, admissionResponse);
                return;
            }

            // ensure that storage is enabled on the build if storage is not enabled on the build then do not continue. it is possible for the namespace to
            // have it configured (so that the webhook can check the namespace) but for the build to be run without storage
            let cacheEnabled = buildData.metadata != null && buildData.metadata.annotations != null && buildData.metadata.annotations[ANNOTATION_CACHE_ENABLED] != null && buildData.metadata.annotations[ANNOTATION_CACHE_ENABLED] === "true";
            if (!cacheEnabled) {
                console.log(`cache is not enabled for pod ${podName} executing build ${buildName} (annotation not found on build)`)
                respondOk(res, admissionResponse);
                return;
            }

            // get the build configuration instance
            let buildConfigName = buildData.metadata.labels['openshift.io/build-config.name'];
            getApiResource("build.openshift.io", namespace, "buildconfigs", buildConfigName, function (err, buildConfigData) {
                if (err != null) {
                    respondOk(res, admissionResponse);
                    console.error(err);
                    return;
                }

                // ensure that the syndo cache enabled annotation is on the build configuration
                if(buildConfigData != null && buildConfigData.kind != null && buildConfigData.metadata != null && buildConfigData.metadata.annotations != null && buildConfigData.metadata.annotations[ANNOTATION_CACHE_ENABLED] != null && buildConfigData.metadata.annotations[ANNOTATION_CACHE_ENABLED] === "true") {
                    // if we can find a pvc name then patch the pod
                    let claimName = buildConfigData.metadata.annotations['syndo/cache-claim-name'];
                    if (claimName != null && claimName !== "") {
                        console.log(`cache is enabled for pod ${podName} executing build config ${buildConfigName}, using claim ${claimName}`)

                        // find the `/var/lib/containers/storage` volume mount index and replace the
                        // emptydir with a subpath mount on the cache-volume
                        let containerMountIndex = -1;
                        if (object.spec != null && object.spec.containers != null && object.spec.containers[0] != null && object.spec.containers[0].volumeMounts != null && object.spec.containers[0].volumeMounts.length > 0) {
                            for (let idx = 0; idx < object.spec.containers[0].volumeMounts.length; idx++) {
                                if (object.spec.containers[0].volumeMounts[idx].mountPath === "/var/lib/containers/storage") {
                                    containerMountIndex = idx;
                                    break;
                                }
                            }
                        }

                        // manufacture patch
                        let patch = [
                            // add metadata to show that it is already configured
                            {
                                "op": "add",
                                "path": "/metadata/annotations",
                                "value": {
                                    "syndo/cache-configured": "true"
                                }
                            },
                            // add the volume to the volumes list
                            {
                                "op": "add",
                                "path": `/spec/volumes/-`,
                                "value": {
                                    "name": "cache-volume",
                                    "persistentVolumeClaim": {
                                        "claimName": `${claimName}`
                                    }
                                }
                            },
                            // add the cache dir volume mount to the container
                            {
                                "op": "add",
                                "path": `/spec/containers/0/volumeMounts/-`,
                                "value": {
                                    "mountPath": "/cache",
                                    "name": "cache-volume",
                                    "subPath": "cache"
                                }
                            },
                            // give a cached runroot as well
                            {
                                "op": "add",
                                "path": `/spec/containers/0/volumeMounts/-`,
                                "value": {
                                    "mountPath": "/run/containers/storage",
                                    "name": "cache-volume",
                                    "subPath": "runroot"
                                }
                            },
                            // and cached shared storage
                            {
                                "op": "add",
                                "path": `/spec/containers/0/volumeMounts/-`,
                                "value": {
                                    "mountPath": "/var/lib/shared",
                                    "name": "cache-volume",
                                    "subPath": "shared"
                                }
                            }
                        ];

                        // if a container volume mount was found replace/overwrite the index
                        if (containerMountIndex >= 0) {
                            patch.push({
                                "op": "replace",
                                "path": `/spec/containers/0/volumeMounts/${containerMountIndex}`,
                                "value": {
                                    "mountPath": "/var/lib/containers/storage",
                                    "name": "cache-volume",
                                    "subPath": "containers"
                                }
                            })
                        }

                        // update the admission response with the patch
                        // with additional help from: https://github.com/nsubrahm/k8s-mutating-webhook/blob/master/webhook/app/mutate.js
                        admissionResponse.response['patch'] = base64.encode(JSON.stringify(patch));
                    } else {
                        console.log(`cache is enabled for pod ${podName} executing build config ${buildConfigName} but no claim given`)
                    }
                } else {
                    console.log(`cache is not enabled for pod ${podName} executing build config ${buildConfigName} (annotation not on buildconfig)`)
                }

                // respond
                respondOk(res, admissionResponse)
            })
        });
    } else {
        // just respond
        respondOk(res, admissionResponse);
    }
});

// module export
module.exports = router;
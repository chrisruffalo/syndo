package io.github.chrisruffalo.syndo.executions.actions.impl;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.chrisruffalo.syndo.config.Root;
import io.github.chrisruffalo.syndo.executions.actions.BaseAction;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ManageSecretsAction extends BaseAction  {

    @Override
    public void build(BuildContext context) {
        // for each secret in the configuration ensure that the secret exists in the target namespace
        final Root config = context.getConfig();
        if (config.getSecrets() == null || config.getSecrets().isEmpty()) {
            logger().debug("No secrets provided for build");
            return;
        }

        final OpenShiftClient client = context.getClient();

        // insert/update each secret
        config.getSecrets().forEach(secret -> {
            if (secret.getName() == null || secret.getName().isEmpty()) {
                logger().error("A secret must have a name");
                return;
            }

            if (secret.getContents() == null || secret.getContents().isEmpty()) {
                logger().warn("Could not create secret {}, secret has no content items", secret.getName());
                return;
            }

            final SecretBuilder builder = new SecretBuilder()
                    .withMetadata(new ObjectMetaBuilder().withName(secret.getName()).withNamespace(context.getNamespace()).build());

            if (secret.getType() != null && !secret.getType().isEmpty()) {
                builder.withType(secret.getType());
            }

            secret.getContents().forEach(content -> {
                if (content.getKey() == null || content.getKey().isEmpty()) {
                    logger().error("A content entry must have a key");
                    return;
                }

                boolean resolvedFile = false;
                String data = "";
                if (content.getFile() != null && !content.getFile().isEmpty()) {
                    Path file = Paths.get(content.getFile());
                    if (!file.isAbsolute() && !Files.exists(file)) {
                        file = context.getConfigPath().getParent().resolve(file);
                    }
                    file = file.normalize().toAbsolutePath();
                    resolvedFile = Files.exists(file);
                    if (resolvedFile) {
                        try {
                            data = new String(Files.readAllBytes(file));
                        } catch (IOException e) {
                            logger().error("Could not load secret {} data for key {} from file {}", secret.getName(), content.getKey(), file);
                        }
                    } else {
                        logger().warn("Could not find secret contents file {} for {}.{}", file, secret.getName(), content.getKey());
                    }
                }

                if (data.isEmpty()) {
                    data = content.getData();
                }

                if (data != null && !data.isEmpty()) {
                    builder.addToStringData(content.getKey(), data);
                } else {
                    logger().error("Could not get content for secret {} key {}", secret.getName(), content.getKey());
                }
            });

            final Secret ocpSecret = builder.build();
            if ((ocpSecret.getData() == null || ocpSecret.getData().isEmpty()) && (ocpSecret.getStringData() == null || ocpSecret.getStringData().isEmpty())) {
                logger().error("The secret {} contains no data", ocpSecret.getMetadata().getName());
                return;
            }

            // delete secret before creating
            if (client.secrets().inNamespace(context.getNamespace()).withName(secret.getName()).get() != null) {
                client.secrets().inNamespace(context.getNamespace()).withName(secret.getName()).delete();
            }

            client.secrets().createOrReplace(builder.build());
            logger().info("Secret {} updated", secret.getName());
        });
    }
}

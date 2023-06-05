package com.brandwatch.kafka_pod_autoscaler.testing;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.client.KubernetesClient;

public final class KustomizeHelper {
    public static void applyKustomize(KubernetesClient client, String path, String namespace) throws IOException, InterruptedException {
        var process = Runtime.getRuntime().exec("kustomize build " + path);

        try (var yamlStream = process.getInputStream()) {
            List<HasMetadata> resources = client.load(yamlStream).items();
            resources.forEach(hm -> {
                hm.getMetadata().setNamespace(namespace);
                if (hm.getKind().toLowerCase(Locale.ROOT).equals("clusterrolebinding")) {
                    var crb = (ClusterRoleBinding) hm;
                    for (var subject : crb.getSubjects()) {
                        subject.setNamespace(namespace);
                    }
                }
            });
            client.resourceList(resources)
                  .inNamespace(namespace)
                  .serverSideApply();
        }
        if (process.waitFor() != 0) {
            process.getErrorStream().transferTo(System.out);
            throw new RuntimeException("Kustomize exited with status " + process.exitValue());
        }
    }
}

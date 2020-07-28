package ar.com.javacuriosities.kubectl.quotas;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscalerSpec;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import picocli.CommandLine;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Main {
    public static void main(String[] args) {
        Parameters parameters = new Parameters();

        new CommandLine(parameters).parseArgs(args);

        if (parameters.isUsageHelpRequested()) {
            CommandLine.usage(new Parameters(), System.out);
            return;
        }

        Map<String, Usage> usage = new HashMap<>();

        Config config = new ConfigBuilder().build();
        KubernetesClient client = new DefaultKubernetesClient(config);

        List<Deployment> deployments = client.apps().deployments().inNamespace(parameters.getNamespace()).list().getItems();

        for (Deployment deployment : deployments) {
            DeploymentSpec spec = deployment.getSpec();
            ObjectMeta metadata = deployment.getMetadata();

            BigDecimal cpuLimit = new BigDecimal(0);
            BigDecimal cpuRequest = new BigDecimal(0);

            BigDecimal memoryLimit = new BigDecimal(0);
            BigDecimal memoryRequest = new BigDecimal(0);

            List<Container> containers = deployment.getSpec().getTemplate().getSpec().getContainers();

            for (Container container : containers) {
                cpuLimit = cpuLimit.add(Quantity.getAmountInBytes(container.getResources().getLimits().get("cpu")));
                cpuRequest = cpuRequest.add(Quantity.getAmountInBytes(container.getResources().getRequests().get("cpu")));

                memoryLimit = memoryLimit.add(Quantity.getAmountInBytes(container.getResources().getLimits().get("memory")));
                memoryRequest = memoryRequest.add(Quantity.getAmountInBytes(container.getResources().getRequests().get("memory")));
            }

            usage.put(deployment.getMetadata().getName(), new Usage(metadata.getName(), spec.getReplicas(), cpuLimit, cpuRequest, memoryLimit, memoryRequest));
        }

        List<HorizontalPodAutoscaler> scaling = client.autoscaling().v1().horizontalPodAutoscalers().inNamespace(parameters.getNamespace()).list().getItems();

        for (HorizontalPodAutoscaler hpa : scaling) {
            ObjectMeta metadata = hpa.getMetadata();
            HorizontalPodAutoscalerSpec spec = hpa.getSpec();

            Usage deploymentUsage = usage.get(hpa.getMetadata().getName());

            if (deploymentUsage != null) {
                usage.put(hpa.getMetadata().getName(), new Usage(metadata.getName(), spec.getMaxReplicas(), deploymentUsage.cpuLimit, deploymentUsage.cpuRequest, deploymentUsage.memoryLimit, deploymentUsage.memoryRequest));
            }
        }

        BigDecimal cpuLimit = new BigDecimal(0);
        BigDecimal cpuRequest = new BigDecimal(0);

        BigDecimal memoryLimit = new BigDecimal(0);
        BigDecimal memoryRequest = new BigDecimal(0);

        for (Entry<String, Usage> entry : usage.entrySet()) {
            Usage value = entry.getValue();

            BigDecimal replicas = new BigDecimal(value.replicas);

            cpuLimit = cpuLimit.add(value.cpuLimit.multiply(replicas));
            cpuRequest = cpuRequest.add(value.cpuRequest.multiply(replicas));

            memoryLimit = memoryLimit.add(value.memoryLimit.multiply(replicas));
            memoryRequest = memoryRequest.add(value.memoryRequest.multiply(replicas));
        }

        BigDecimal percentage = new BigDecimal(1 + (parameters.getPercentage() / 100));

        System.out.println("limits.cpu --> " + cpuLimit.multiply(percentage).toBigInteger());
        System.out.println("limits.memory --> " + memoryLimit.divide(new BigDecimal(1024)).divide(new BigDecimal(1024)).multiply(percentage).toBigInteger());

        System.out.println("requests.cpu --> " + cpuRequest.multiply(percentage).toBigInteger());
        System.out.println("requests.memory --> " + memoryRequest.divide(new BigDecimal(1024)).divide(new BigDecimal(1024)).multiply(percentage).toBigInteger());
    }

    private static class Usage {
        private final String name;

        private final int replicas;

        private final BigDecimal cpuLimit;
        private final BigDecimal cpuRequest;

        private final BigDecimal memoryLimit;
        private final BigDecimal memoryRequest;

        public Usage(String name, int replicas, BigDecimal cpuLimit, BigDecimal cpuRequest, BigDecimal memoryLimit, BigDecimal memoryRequest) {
            this.name = name;
            this.replicas = replicas;
            this.cpuLimit = cpuLimit;
            this.cpuRequest = cpuRequest;
            this.memoryLimit = memoryLimit;
            this.memoryRequest = memoryRequest;
        }

        @Override
        public String toString() {
            return "Usage{" +
                    "name='" + name + '\'' +
                    ", replicas=" + replicas +
                    ", cpuLimit=" + cpuLimit +
                    ", cpuRequest=" + cpuRequest +
                    ", memoryLimit=" + memoryLimit +
                    ", memoryRequest=" + memoryRequest +
                    '}';
        }
    }
}

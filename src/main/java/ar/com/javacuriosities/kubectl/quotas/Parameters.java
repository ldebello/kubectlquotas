package ar.com.javacuriosities.kubectl.quotas;

import picocli.CommandLine.Option;

public class Parameters {

    @Option(names = {"-n", "--namespace"}, description = "Namespace to compute quotas", required = true)
    private String namespace;

    @Option(names = {"-p", "--percentage"}, description = "Namespace to compute quotas", defaultValue = "20")
    private double percentage;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display help message")
    private boolean usageHelpRequested;

    public String getNamespace() {
        return namespace;
    }

    public double getPercentage() {
        return percentage;
    }

    public boolean isUsageHelpRequested() {
        return usageHelpRequested;
    }
}

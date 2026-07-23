package copybench;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.client.RestClientImpl;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysis;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.SecurityAnalysisRunParameters;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Benchmarks OLF multi-thread security analysis on a network-store network (local server + postgres):
 * REBUILD (legacy one-LfNetwork-build-per-thread) vs COPY (build once, deep copy per thread).
 *
 * Args: uuid [threadCount] [factory: simple|jdk]
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        UUID uuid = UUID.fromString(args[0]);
        int threadCount = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        boolean jdkFactory = args.length > 2 && args[2].equals("jdk");
        String only = args.length > 3 ? args[3] : "all";
        PreloadingStrategy strategy = args.length > 4 && args[4].equals("busview")
                ? PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW
                : args.length > 4 && args[4].equals("computation")
                ? PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_COMPUTATION
                : PreloadingStrategy.COLLECTION;

        String baseUrl = System.getenv().getOrDefault("STORE_URL", "http://localhost:8080");
        RestTemplateBuilder builder = RestClientImpl.createRestTemplateBuilder(baseUrl);
        if (!jdkFactory) {
            builder = builder.requestFactory((Supplier<ClientHttpRequestFactory>) SimpleClientHttpRequestFactory::new);
        }
        try (NetworkStoreService service = new NetworkStoreService(new RestClientImpl(builder), strategy)) {
            long readStart = System.nanoTime();
            Network network = service.getNetwork(uuid);
            List<Contingency> contingencies = network.getLineStream()
                    .map(l -> Contingency.line(l.getId()))
                    .toList();
            System.out.printf(Locale.US, "== read from store: %.0f ms%n", ms(readStart));
            System.out.println("== contingencies: " + contingencies.size() + ", threads: " + threadCount
                    + ", http factory: " + (jdkFactory ? "jdk" : "simple"));

            if (only.equals("all")) {
                // single thread reference (also JIT warmup for everything below)
                Result st = run(network, contingencies, 1, null);
                // interleave the two modes, two runs each: second run of each is the warm one
                Result rebuild1 = run(network, contingencies, threadCount, OpenSecurityAnalysisParameters.NetworkPerThreadMode.REBUILD);
                Result copy1 = run(network, contingencies, threadCount, OpenSecurityAnalysisParameters.NetworkPerThreadMode.COPY);
                Result rebuild2 = run(network, contingencies, threadCount, OpenSecurityAnalysisParameters.NetworkPerThreadMode.REBUILD);
                Result copy2 = run(network, contingencies, threadCount, OpenSecurityAnalysisParameters.NetworkPerThreadMode.COPY);

                System.out.printf(Locale.US, "%n== TIMES (ms) single=%.0f rebuild=%.0f/%.0f copy=%.0f/%.0f%n",
                        st.millis, rebuild1.millis, rebuild2.millis, copy1.millis, copy2.millis);
                System.out.println("== rebuild == single-thread results: " + st.description.equals(rebuild2.description));
                System.out.println("== copy == single-thread results: " + st.description.equals(copy2.description));
                System.out.println("== copy == rebuild results: " + rebuild2.description.equals(copy2.description));
            } else if (only.equals("verify")) {
                // single thread reference vs multi thread copy mode: results must be identical
                Result st = run(network, contingencies, 1, null);
                Result copy = run(network, contingencies, threadCount, OpenSecurityAnalysisParameters.NetworkPerThreadMode.COPY);
                System.out.println("== copy == single-thread results: " + st.description().equals(copy.description()));
            } else if (only.equals("verifyfull")) {
                // same but with everything that used to force iidm reads on the worker threads:
                // state monitors, result extensions and an operator strategy with actions
                java.util.List<String> monitoredBranches = network.getLineStream().limit(10)
                        .map(com.powsybl.iidm.network.Line::getId).toList();
                java.util.List<String> monitoredVls = network.getVoltageLevelStream().limit(5)
                        .map(com.powsybl.iidm.network.VoltageLevel::getId).toList();
                java.util.List<com.powsybl.security.monitor.StateMonitor> monitors = java.util.List.of(
                        new com.powsybl.security.monitor.StateMonitor(com.powsybl.contingency.ContingencyContext.all(),
                                new java.util.HashSet<>(monitoredBranches), new java.util.HashSet<>(monitoredVls), java.util.Set.of()));
                String loadId = network.getLoadStream().findFirst().orElseThrow().getId();
                String lineToOpen = contingencies.get(1).getId();
                java.util.List<com.powsybl.action.Action> actions = java.util.List.of(
                        new com.powsybl.action.LoadActionBuilder().withId("loadUp").withLoadId(loadId)
                                .withRelativeValue(true).withActivePowerValue(5).build(),
                        new com.powsybl.action.TerminalsConnectionAction("openLine", lineToOpen, true));
                java.util.List<com.powsybl.contingency.strategy.OperatorStrategy> strategies = java.util.List.of(
                        new com.powsybl.contingency.strategy.OperatorStrategy("strategy1",
                                com.powsybl.contingency.ContingencyContext.specificContingency(contingencies.get(0).getId()),
                                java.util.List.of(new com.powsybl.contingency.strategy.ConditionalActions("stage1",
                                        new com.powsybl.contingency.strategy.condition.TrueCondition(),
                                        java.util.List.of("loadUp", "openLine")))));
                Result st = runFull(network, contingencies, 1, null, monitors, strategies, actions);
                Result copy = runFull(network, contingencies, threadCount,
                        OpenSecurityAnalysisParameters.NetworkPerThreadMode.COPY, monitors, strategies, actions);
                System.out.println("== full copy == single-thread results: " + st.description().equals(copy.description()));
            } else if (only.equals("clonelfflush")) {
                // same as clonelf but each worker flushes the load flow results to the server
                // before removing its variant: exercises the buffered write path, concurrently
                int flushLineCount = args.length > 5 ? Integer.parseInt(args[5]) : Integer.MAX_VALUE;
                java.util.List<String> flushLineIds = network.getLineStream()
                        .map(com.powsybl.iidm.network.Line::getId)
                        .limit(flushLineCount).toList();
                String flushRunId = Long.toHexString(System.currentTimeMillis() & 0xffffff);
                runCloneLfFlush(service, network, flushLineIds.subList(0, Math.min(5, flushLineIds.size())), 1, flushRunId + "w");
                var fseq = runCloneLfFlush(service, network, flushLineIds, 1, flushRunId + "s");
                var fpar = runCloneLfFlush(service, network, flushLineIds, threadCount, flushRunId + "p");
                System.out.println("== clonelfflush == parallel == sequential results: " + fseq.equals(fpar));
            } else if (only.equals("clonelf")) {
                // gridsuite style clone-per-contingency: clone variant, apply outage, run a plain
                // load flow on the variant, digest results, remove the variant
                int lineCount = args.length > 5 ? Integer.parseInt(args[5]) : Integer.MAX_VALUE;
                java.util.List<String> lineIds = network.getLineStream()
                        .map(com.powsybl.iidm.network.Line::getId)
                        .limit(lineCount).toList();
                // warmup (JIT + fills the collection cache); unique run tag to survive leftovers
                String runId = Long.toHexString(System.currentTimeMillis() & 0xffffff);
                runCloneLf(network, lineIds.subList(0, Math.min(5, lineIds.size())), 1, runId + "w");
                var seq = runCloneLf(network, lineIds, 1, runId + "s");
                var par = runCloneLf(network, lineIds, threadCount, runId + "p");
                System.out.println("== clonelf == parallel == sequential results: " + seq.equals(par));
            } else if (only.equals("sensi")) {
                // multi-thread COPY sensitivity vs single thread: values must be identical
                String stDesc = runSensi(network, contingencies, 1);
                String mtDesc = runSensi(network, contingencies, threadCount);
                System.out.println("== sensi copy == single-thread results: " + stDesc.equals(mtDesc));
            } else {
                // single mode, cold client cache in this fresh JVM: worst case for per-thread IIDM traversal
                run(network, contingencies, threadCount,
                        OpenSecurityAnalysisParameters.NetworkPerThreadMode.valueOf(only.toUpperCase(Locale.US)));
            }
        }
        System.out.println("== done");
    }

    private record Result(double millis, String description) {
    }

    private static Result runFull(Network network, List<Contingency> contingencies, int threadCount,
                                  OpenSecurityAnalysisParameters.NetworkPerThreadMode mode,
                                  List<com.powsybl.security.monitor.StateMonitor> monitors,
                                  List<com.powsybl.contingency.strategy.OperatorStrategy> strategies,
                                  List<com.powsybl.action.Action> actions) {
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();
        OpenSecurityAnalysisParameters olfParameters = new OpenSecurityAnalysisParameters()
                .setThreadCount(threadCount)
                .setCreateResultExtension(true);
        if (mode != null) {
            olfParameters.setNetworkPerThreadMode(mode);
        }
        parameters.addExtension(OpenSecurityAnalysisParameters.class, olfParameters);
        long start = System.nanoTime();
        SecurityAnalysisResult result = SecurityAnalysis.run(network, contingencies,
                SecurityAnalysisRunParameters.getDefault()
                        .setSecurityAnalysisParameters(parameters)
                        .setMonitors(monitors)
                        .setOperatorStrategies(strategies)
                        .setActions(actions)).getResult();
        double millis = ms(start);
        System.out.printf(Locale.US, "== runFull threads=%d mode=%s: %.0f ms (%d branch results, %d bus results, %d strategy results)%n",
                threadCount, mode == null ? "-" : mode, millis,
                result.getPreContingencyResult().getNetworkResult().getBranchResults().size(),
                result.getPreContingencyResult().getNetworkResult().getBusResults().size(),
                result.getOperatorStrategyResults().size());
        return new Result(millis, describe(result) + describeNetworkResults(result));
    }

    private static String describeNetworkResults(SecurityAnalysisResult result) {
        StringBuilder description = new StringBuilder();
        result.getPostContingencyResults().stream()
                .sorted(Comparator.comparing(r -> r.getContingency().getId()))
                .forEach(r -> {
                    description.append(r.getContingency().getId()).append(" network results:");
                    r.getNetworkResult().getBranchResults().stream()
                            .map(br -> String.format(Locale.US, " %s p1=%.1f i1=%.1f", br.getBranchId(), br.getP1(), br.getI1()))
                            .sorted()
                            .forEach(description::append);
                    r.getNetworkResult().getBusResults().stream()
                            .map(br -> String.format(Locale.US, " %s/%s v=%.2f", br.getVoltageLevelId(), br.getBusId(), br.getV()))
                            .sorted()
                            .forEach(description::append);
                    description.append('\n');
                });
        result.getOperatorStrategyResults().forEach(osr ->
                description.append(osr.getOperatorStrategy().getId()).append(' ').append(osr.getStatus()).append('\n'));
        return description.toString();
    }

    private static java.util.Map<String, String> runCloneLfFlush(NetworkStoreService service, Network network, List<String> lineIds,
                                                                 int threadCount, String tag) throws Exception {
        com.powsybl.iidm.network.VariantManager vm = network.getVariantManager();
        if (threadCount > 1) {
            vm.allowVariantMultiThreadAccess(true);
        }
        com.powsybl.loadflow.LoadFlowParameters lfParameters = new com.powsybl.loadflow.LoadFlowParameters();
        java.util.concurrent.ConcurrentHashMap<String, String> digests = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.concurrent.atomic.AtomicLong flushNanos = new java.util.concurrent.atomic.AtomicLong();
        long start = System.nanoTime();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (String lineId : lineIds) {
            futures.add(pool.submit(() -> {
                String variantId = tag + "_" + lineId;
                vm.cloneVariant(com.powsybl.iidm.network.VariantManagerConstants.INITIAL_VARIANT_ID, variantId);
                vm.setWorkingVariant(variantId);
                com.powsybl.iidm.network.Line line = network.getLine(lineId);
                line.getTerminal1().disconnect();
                line.getTerminal2().disconnect();
                com.powsybl.loadflow.LoadFlowResult result = com.powsybl.loadflow.LoadFlow.run(network, lfParameters);
                long flushStart = System.nanoTime();
                service.flushWorkingVariant(network);
                flushNanos.addAndGet(System.nanoTime() - flushStart);
                StringBuilder digest = new StringBuilder();
                digest.append(result.getComponentResults().get(0).getStatus());
                double vSum = network.getBusView().getBusStream()
                        .mapToDouble(com.powsybl.iidm.network.Bus::getV)
                        .filter(v -> !Double.isNaN(v))
                        .sum();
                digest.append(String.format(Locale.US, " vSum=%.4f", vSum));
                digests.put(lineId, digest.toString());
                vm.removeVariant(variantId);
                return null;
            }));
        }
        try {
            for (var f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        double millis = ms(start);
        System.out.printf(Locale.US, "== runCloneLfFlush %s threads=%d: %.0f ms total, %.0f ms cumulated in flush (%d contingencies)%n",
                tag, threadCount, millis, flushNanos.get() / 1e6, lineIds.size());
        return digests;
    }

    private static java.util.Map<String, String> runCloneLf(Network network, List<String> lineIds, int threadCount, String tag) throws Exception {
        com.powsybl.iidm.network.VariantManager vm = network.getVariantManager();
        if (threadCount > 1) {
            vm.allowVariantMultiThreadAccess(true);
        }
        com.powsybl.loadflow.LoadFlowParameters lfParameters = new com.powsybl.loadflow.LoadFlowParameters();
        java.util.concurrent.ConcurrentHashMap<String, String> digests = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.concurrent.atomic.AtomicLong cloneNanos = new java.util.concurrent.atomic.AtomicLong();
        long start = System.nanoTime();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (String lineId : lineIds) {
            futures.add(pool.submit(() -> {
                String variantId = tag + "_" + lineId;
                long cloneStart = System.nanoTime();
                vm.cloneVariant(com.powsybl.iidm.network.VariantManagerConstants.INITIAL_VARIANT_ID, variantId);
                vm.setWorkingVariant(variantId);
                long cloneNs = System.nanoTime() - cloneStart;
                cloneNanos.addAndGet(cloneNs);
                if (Boolean.getBoolean("clonelf.trace")) {
                    System.out.printf(Locale.US, "  [%s] clone %s: %.0f ms%n", Thread.currentThread().getName(), variantId, cloneNs / 1e6);
                }
                com.powsybl.iidm.network.Line line = network.getLine(lineId);
                line.getTerminal1().disconnect();
                line.getTerminal2().disconnect();
                com.powsybl.loadflow.LoadFlowResult result = com.powsybl.loadflow.LoadFlow.run(network, lfParameters);
                StringBuilder digest = new StringBuilder();
                digest.append(result.getComponentResults().get(0).getStatus());
                double vSum = network.getBusView().getBusStream()
                        .mapToDouble(com.powsybl.iidm.network.Bus::getV)
                        .filter(v -> !Double.isNaN(v))
                        .sum();
                digest.append(String.format(Locale.US, " vSum=%.4f", vSum));
                digests.put(lineId, digest.toString());
                if (Boolean.getBoolean("clonelf.trace")) {
                    System.out.printf(Locale.US, "  [%s] lf+digest done %s%n", Thread.currentThread().getName(), variantId);
                }
                vm.removeVariant(variantId);
                return null;
            }));
        }
        try {
            for (var f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        double millis = ms(start);
        System.out.printf(Locale.US, "== runCloneLf %s threads=%d: %.0f ms total, %.0f ms cumulated in cloneVariant (%d contingencies)%n",
                tag, threadCount, millis, cloneNanos.get() / 1e6, lineIds.size());
        return digests;
    }

    private static String runSensi(Network network, List<Contingency> contingencies, int threadCount) {
        List<String> lines = network.getLineStream().limit(8).map(com.powsybl.iidm.network.Line::getId).toList();
        List<String> injections = new java.util.ArrayList<>(network.getGeneratorStream().limit(3)
                .map(com.powsybl.iidm.network.Generator::getId).toList());
        network.getLoadStream().limit(2).map(com.powsybl.iidm.network.Load::getId).forEach(injections::add);
        List<com.powsybl.sensitivity.SensitivityFactor> factors = new java.util.ArrayList<>();
        for (String line : lines) {
            for (String injection : injections) {
                factors.add(new com.powsybl.sensitivity.SensitivityFactor(
                        com.powsybl.sensitivity.SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, line,
                        com.powsybl.sensitivity.SensitivityVariableType.INJECTION_ACTIVE_POWER, injection,
                        false, com.powsybl.contingency.ContingencyContext.all()));
            }
        }
        com.powsybl.sensitivity.SensitivityAnalysisParameters parameters = new com.powsybl.sensitivity.SensitivityAnalysisParameters();
        parameters.addExtension(com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisParameters.class,
                new com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisParameters()
                        .setThreadCount(threadCount)
                        .setNetworkPerThreadMode(com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisParameters.NetworkPerThreadMode.COPY));
        long start = System.nanoTime();
        com.powsybl.sensitivity.SensitivityAnalysisResult result =
                com.powsybl.sensitivity.SensitivityAnalysis.run(network, factors, contingencies, List.of(), parameters);
        double millis = ms(start);
        System.out.printf(Locale.US, "== runSensi threads=%d mode=COPY: %.0f ms (%d values)%n",
                threadCount, millis, result.getValues().size());
        return result.getValues().stream()
                .map(v -> String.format(Locale.US, "%s|%s|%s value=%.4f ref=%.4f",
                        v.getContingencyIndex() < 0 ? "pre" : contingencies.get(v.getContingencyIndex()).getId(),
                        factors.get(v.getFactorIndex()).getFunctionId(),
                        factors.get(v.getFactorIndex()).getVariableId(),
                        v.getValue(), v.getFunctionReference()))
                .sorted()
                .reduce(new StringBuilder(), (sb, s) -> sb.append(s).append('\n'), StringBuilder::append)
                .toString();
    }

    private static Result run(Network network, List<Contingency> contingencies, int threadCount,
                              OpenSecurityAnalysisParameters.NetworkPerThreadMode mode) {
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();
        OpenSecurityAnalysisParameters olfParameters = new OpenSecurityAnalysisParameters().setThreadCount(threadCount);
        if (mode != null) {
            olfParameters.setNetworkPerThreadMode(mode);
        }
        parameters.addExtension(OpenSecurityAnalysisParameters.class, olfParameters);
        long start = System.nanoTime();
        SecurityAnalysisResult result = SecurityAnalysis.run(network, contingencies,
                SecurityAnalysisRunParameters.getDefault().setSecurityAnalysisParameters(parameters)).getResult();
        double millis = ms(start);
        System.out.printf(Locale.US, "== run threads=%d mode=%s: %.0f ms%n", threadCount, mode == null ? "-" : mode, millis);
        return new Result(millis, describe(result));
    }

    private static double ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1e6;
    }

    // same normalization as SecurityAnalysisImplComparisonTest: statuses and violations, sorted, rounded to 0.1
    private static String describe(SecurityAnalysisResult result) {
        StringBuilder description = new StringBuilder();
        description.append("pre-contingency ").append(result.getPreContingencyResult().getStatus()).append('\n');
        result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().stream()
                .map(Main::describe)
                .sorted()
                .forEach(violation -> description.append(violation).append('\n'));
        result.getPostContingencyResults().stream()
                .sorted(Comparator.comparing(r -> r.getContingency().getId()))
                .forEach(postContingencyResult -> {
                    description.append(postContingencyResult.getContingency().getId())
                            .append(' ').append(postContingencyResult.getStatus()).append('\n');
                    postContingencyResult.getLimitViolationsResult().getLimitViolations().stream()
                            .map(Main::describe)
                            .sorted()
                            .forEach(violation -> description.append(violation).append('\n'));
                });
        return description.toString();
    }

    private static String describe(LimitViolation violation) {
        return String.format(Locale.US, "  %s %s %s limit=%.1f value=%.1f",
                violation.getSubjectId(), violation.getLimitType(), violation.getSide(),
                violation.getLimit(), violation.getValue());
    }
}

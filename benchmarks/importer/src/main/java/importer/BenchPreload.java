package importer;

import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.security.SecurityAnalysis;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.SecurityAnalysisRunParameters;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Times the cold preload of a stored network with the computation strategy (bundle endpoint if the
 * server supports it, parallel per collection loads otherwise), then runs a single thread security
 * analysis and prints a normalized digest of the results, to compare the two server paths.
 *
 * Args: uuid
 */
public final class BenchPreload {

    private BenchPreload() {
    }

    public static void main(String[] args) {
        UUID uuid = UUID.fromString(args[0]);
        String baseUrl = args.length > 1 ? args[1] : "http://localhost:8080";
        try (NetworkStoreService service = new NetworkStoreService(baseUrl, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_COMPUTATION)) {
            long start = System.nanoTime();
            Network network = service.getNetwork(uuid);
            long lineCount = network.getLineStream().count();
            double preloadMs = (System.nanoTime() - start) / 1e6;
            System.out.printf(Locale.US, "== preload (network + all collections): %.0f ms (%d lines)%n", preloadMs, lineCount);

            List<Contingency> contingencies = network.getLineStream()
                    .limit(30)
                    .map(l -> Contingency.line(l.getId()))
                    .toList();
            SecurityAnalysisResult result = SecurityAnalysis.run(network, contingencies, SecurityAnalysisRunParameters.getDefault()).getResult();
            StringBuilder digest = new StringBuilder("pre " + result.getPreContingencyResult().getStatus());
            result.getPostContingencyResults().stream()
                    .sorted(Comparator.comparing(r -> r.getContingency().getId()))
                    .forEach(r -> {
                        digest.append(' ').append(r.getContingency().getId()).append(':').append(r.getStatus()).append(':');
                        r.getLimitViolationsResult().getLimitViolations().stream()
                                .map(v -> String.format(Locale.US, "%s/%s/%.1f", v.getSubjectId(), v.getLimitType(), v.getValue()))
                                .sorted()
                                .forEach(violation -> digest.append(violation).append(','));
                    });
            System.out.println("== sa digest hash: " + digest.toString().hashCode() + " (contingencies: " + contingencies.size() + ")");
        }
        System.out.println("== done");
    }
}

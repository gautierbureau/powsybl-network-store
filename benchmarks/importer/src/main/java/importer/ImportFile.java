package importer;

import com.powsybl.commons.datasource.DataSource;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Imports a network file (any format on the classpath, e.g. a MATPOWER .m case) into the local
 * network-store server and prints its UUID and a size summary.
 *
 * Args: pathToNetworkFile
 */
public final class ImportFile {

    private ImportFile() {
    }

    public static void main(String[] args) {
        Path file = Path.of(args[0]);
        String baseUrl = System.getenv().getOrDefault("STORE_URL", "http://localhost:8080");
        try (NetworkStoreService service = new NetworkStoreService(baseUrl, PreloadingStrategy.COLLECTION)) {
            DataSource dataSource = DataSource.fromPath(file);
            long readStart = System.nanoTime();
            Network network = service.importNetwork(dataSource);
            System.out.printf(Locale.US, "== imported in %.0f ms%n", (System.nanoTime() - readStart) / 1e6);
            long flushStart = System.nanoTime();
            service.flush(network);
            System.out.printf(Locale.US, "== flushed to store in %.0f ms%n", (System.nanoTime() - flushStart) / 1e6);
            System.out.println("UUID=" + service.getNetworkUuid(network));
            System.out.printf(Locale.US, "buses=%d lines=%d 2wt=%d 3wt=%d gens=%d loads=%d shunts=%d%n",
                    network.getBusView().getBusStream().count(),
                    network.getLineCount(),
                    network.getTwoWindingsTransformerCount(),
                    network.getThreeWindingsTransformerCount(),
                    network.getGeneratorCount(),
                    network.getLoadCount(),
                    network.getShuntCompensatorCount());
        }
    }
}

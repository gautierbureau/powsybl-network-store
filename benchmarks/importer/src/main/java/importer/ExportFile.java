package importer;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.serde.ExportOptions;
import com.powsybl.iidm.serde.NetworkSerDe;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Reads a network from the local network-store server by UUID and writes it to an XIIDM file, so it
 * can be post-processed by external tools (e.g. adding operational limit groups or converting to
 * node/breaker) and re-imported.
 *
 * Args: networkUuid output.xiidm
 */
public final class ExportFile {

    private ExportFile() {
    }

    public static void main(String[] args) {
        UUID uuid = UUID.fromString(args[0]);
        Path out = Path.of(args[1]);
        String baseUrl = System.getenv().getOrDefault("STORE_URL", "http://localhost:8080");
        try (NetworkStoreService service = new NetworkStoreService(baseUrl, PreloadingStrategy.COLLECTION)) {
            long start = System.nanoTime();
            Network network = service.getNetwork(uuid);
            NetworkSerDe.write(network, new ExportOptions(), out);
            System.out.printf(Locale.US, "== exported %s to %s in %.0f ms (buses=%d lines=%d 2wt=%d)%n",
                    uuid, out, (System.nanoTime() - start) / 1e6,
                    network.getBusView().getBusStream().count(), network.getLineCount(),
                    network.getTwoWindingsTransformerCount());
        }
    }
}
